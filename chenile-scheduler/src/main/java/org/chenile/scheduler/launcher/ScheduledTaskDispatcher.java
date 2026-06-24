package org.chenile.scheduler.launcher;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.chenile.core.model.ChenileServiceDefinition;
import org.chenile.core.model.OperationDefinition;
import org.chenile.scheduler.model.ScheduledExecutionRequest;
import org.chenile.scheduler.model.SchedulerInfo;
import org.chenile.scheduler.store.SchedulerExecutionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScheduledTaskDispatcher {
	private static final Logger logger = LoggerFactory.getLogger(ScheduledTaskDispatcher.class);

	private final Map<String,ScheduledTaskLauncher> launchers;
	private final SchedulerExecutionStore executionStore;
	private final String defaultLauncher;

	public ScheduledTaskDispatcher(Map<String,ScheduledTaskLauncher> launchers,
			SchedulerExecutionStore executionStore, String defaultLauncher) {
		this.launchers = new HashMap<>(launchers);
		this.executionStore = executionStore;
		this.defaultLauncher = normalize(defaultLauncher);
	}

	public void dispatch(SchedulerInfo schedulerInfo, ChenileServiceDefinition serviceDefinition,
			OperationDefinition operationDefinition, Date scheduledFireTime, Date actualFireTime) throws Exception {
		applyDefaults(schedulerInfo);
		ScheduledExecutionRequest request = request(schedulerInfo, serviceDefinition, operationDefinition,
				scheduledFireTime, actualFireTime);
		if (!executionStore.tryStartExecution(request)) {
			logger.info("Skipping duplicate scheduled execution {}", request.getExecutionId());
			return;
		}
		ScheduledTaskLauncher launcher = launchers.get(request.getLauncher());
		if (launcher == null) {
			executionStore.markFailure(request.getExecutionId(), request.getAttempt(),
					"No scheduler launcher configured for " + request.getLauncher());
			throw new IllegalStateException("No scheduler launcher configured for " + request.getLauncher());
		}
		if (!launcher.completesSynchronously()) {
			launchOnce(launcher, request);
			return;
		}
		launchWithLocalRetries(launcher, request);
	}

	private void launchWithLocalRetries(ScheduledTaskLauncher launcher, ScheduledExecutionRequest request)
			throws Exception {
		int maxAttempt = effectiveRetryCount(request.getSchedulerInfo()) + 1;
		Exception lastException = null;
		for (int attempt = 1; attempt <= maxAttempt; attempt++) {
			request.setAttempt(attempt);
			try {
				LaunchResult result = launchWithOptionalTimeout(launcher, request);
				executionStore.markSuccess(request.getExecutionId(), attempt, result.getLauncherMetadata());
				logger.info("Scheduled execution {} completed on attempt {}", request.getExecutionId(), attempt);
				return;
			} catch (TimeoutException e) {
				executionStore.markTimedOut(request.getExecutionId(), attempt, e.getMessage());
				throw e;
			} catch (Exception e) {
				lastException = e;
				logger.warn("Scheduled execution {} failed on attempt {}", request.getExecutionId(), attempt, e);
			}
		}
		executionStore.markFailure(request.getExecutionId(), maxAttempt,
				lastException == null ? null : lastException.getMessage());
		throw lastException;
	}

	private void launchOnce(ScheduledTaskLauncher launcher, ScheduledExecutionRequest request) throws Exception {
		try {
			LaunchResult result = launcher.launch(request);
			executionStore.markSuccess(request.getExecutionId(), request.getAttempt(), result.getLauncherMetadata());
			logger.info("Dispatched scheduled execution {}", request.getExecutionId());
		} catch (Exception e) {
			executionStore.markFailure(request.getExecutionId(), request.getAttempt(), e.getMessage());
			throw e;
		}
	}

	private LaunchResult launchWithOptionalTimeout(ScheduledTaskLauncher launcher, ScheduledExecutionRequest request)
			throws Exception {
		Integer timeoutSeconds = request.getSchedulerInfo().getTimeoutSeconds();
		if (timeoutSeconds == null || timeoutSeconds <= 0) {
			return launcher.launch(request);
		}
		ExecutorService executorService = Executors.newSingleThreadExecutor();
		try {
			Future<LaunchResult> future = executorService.submit(() -> launcher.launch(request));
			return future.get(timeoutSeconds, TimeUnit.SECONDS);
		} catch (TimeoutException e) {
			throw new TimeoutException("Scheduled execution timed out after " + timeoutSeconds + " seconds");
		} finally {
			executorService.shutdownNow();
		}
	}

	private ScheduledExecutionRequest request(SchedulerInfo schedulerInfo,
			ChenileServiceDefinition serviceDefinition, OperationDefinition operationDefinition,
			Date scheduledFireTime, Date actualFireTime) {
		Instant scheduledAt = toInstant(scheduledFireTime);
		ScheduledExecutionRequest request = new ScheduledExecutionRequest();
		request.setSchedulerInfo(schedulerInfo);
		request.setServiceDefinition(serviceDefinition);
		request.setOperationDefinition(operationDefinition);
		request.setScheduledFireTime(scheduledAt);
		request.setActualFireTime(toInstant(actualFireTime));
		request.setExecutionId(schedulerInfo.getJobName() + "-" + scheduledAt.toEpochMilli());
		request.setAttempt(1);
		return request;
	}

	private Instant toInstant(Date date) {
		return date == null ? Instant.now() : date.toInstant();
	}

	private void applyDefaults(SchedulerInfo schedulerInfo) {
		if (schedulerInfo.getLauncher() == null) {
			schedulerInfo.setLauncher(defaultLauncher);
		}
	}

	private int effectiveRetryCount(SchedulerInfo info) {
		return info.getRetryCount() == null ? 0 : info.getRetryCount();
	}

	private String normalize(String value) {
		return value == null ? null : value.trim().toLowerCase().replace('_', '-');
	}
}
