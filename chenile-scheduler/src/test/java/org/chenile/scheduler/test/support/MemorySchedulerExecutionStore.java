package org.chenile.scheduler.test.support;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.chenile.scheduler.model.ScheduledExecutionRecord;
import org.chenile.scheduler.model.ScheduledExecutionRequest;
import org.chenile.scheduler.model.SchedulerExecutionStatus;
import org.chenile.scheduler.store.SchedulerExecutionStore;

public class MemorySchedulerExecutionStore implements SchedulerExecutionStore {
	private final Map<String,ScheduledExecutionRecord> executions = new ConcurrentHashMap<>();

	@Override
	public boolean tryStartExecution(ScheduledExecutionRequest request) {
		ScheduledExecutionRecord record = new ScheduledExecutionRecord();
		record.setExecutionId(request.getExecutionId());
		record.setJobName(request.getJobName());
		record.setScheduledFireTime(request.getScheduledFireTime());
		record.setActualFireTime(request.getActualFireTime());
		record.setStartedAt(Instant.now());
		record.setStatus(SchedulerExecutionStatus.RUNNING);
		record.setAttempt(request.getAttempt());
		record.setLauncher(request.getLauncher());
		return executions.putIfAbsent(request.getExecutionId(), record) == null;
	}

	@Override
	public void markSuccess(String executionId, int attempt, String launcherMetadata) {
		update(executionId, attempt, SchedulerExecutionStatus.SUCCESS, null, launcherMetadata);
	}

	@Override
	public void markFailure(String executionId, int attempt, String errorMessage) {
		update(executionId, attempt, SchedulerExecutionStatus.FAILED, errorMessage, null);
	}

	@Override
	public void markTimedOut(String executionId, int attempt, String errorMessage) {
		update(executionId, attempt, SchedulerExecutionStatus.TIMED_OUT, errorMessage, null);
	}

	@Override
	public Optional<ScheduledExecutionRecord> findByExecutionId(String executionId) {
		return Optional.ofNullable(executions.get(executionId));
	}

	private void update(String executionId, int attempt, SchedulerExecutionStatus status,
			String errorMessage, String launcherMetadata) {
		ScheduledExecutionRecord record = executions.get(executionId);
		if (record == null) {
			return;
		}
		Instant finishedAt = Instant.now();
		record.setFinishedAt(finishedAt);
		record.setAttempt(attempt);
		record.setStatus(status);
		record.setErrorMessage(errorMessage);
		if (record.getStartedAt() != null) {
			record.setDurationMillis(Duration.between(record.getStartedAt(), finishedAt).toMillis());
		}
		if (launcherMetadata != null) {
			record.setLauncherMetadata(launcherMetadata);
		}
	}
}
