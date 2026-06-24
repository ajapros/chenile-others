package org.chenile.scheduler.store;

import java.util.Optional;

import org.chenile.scheduler.model.ScheduledExecutionRecord;
import org.chenile.scheduler.model.ScheduledExecutionRequest;

public interface SchedulerExecutionStore {
	boolean tryStartExecution(ScheduledExecutionRequest request);
	void markSuccess(String executionId, int attempt, String launcherMetadata);
	void markFailure(String executionId, int attempt, String errorMessage);
	void markTimedOut(String executionId, int attempt, String errorMessage);
	Optional<ScheduledExecutionRecord> findByExecutionId(String executionId);
}
