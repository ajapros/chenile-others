package org.chenile.scheduler.service;

import java.util.Optional;

import org.chenile.scheduler.model.ScheduledExecutionRecord;
import org.chenile.scheduler.store.SchedulerExecutionStore;

public class SchedulerExecutionService {
	private final SchedulerExecutionStore executionStore;

	public SchedulerExecutionService(SchedulerExecutionStore executionStore) {
		this.executionStore = executionStore;
	}

	public void markSuccess(String executionId, String launcherMetadata) {
		executionStore.markSuccess(executionId, 1, launcherMetadata);
	}

	public void markFailure(String executionId, String errorMessage) {
		executionStore.markFailure(executionId, 1, errorMessage);
	}

	public void markTimedOut(String executionId, String errorMessage) {
		executionStore.markTimedOut(executionId, 1, errorMessage);
	}

	public Optional<ScheduledExecutionRecord> findByExecutionId(String executionId) {
		return executionStore.findByExecutionId(executionId);
	}
}
