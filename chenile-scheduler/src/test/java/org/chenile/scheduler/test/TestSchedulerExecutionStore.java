package org.chenile.scheduler.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Instant;

import org.chenile.scheduler.model.ScheduledExecutionRequest;
import org.chenile.scheduler.model.SchedulerInfo;
import org.chenile.scheduler.test.support.MemorySchedulerExecutionStore;
import org.junit.Test;

public class TestSchedulerExecutionStore {
	@Test public void duplicateExecutionsAreRejected() {
		MemorySchedulerExecutionStore store = new MemorySchedulerExecutionStore();
		ScheduledExecutionRequest request = request("job1-1000");
		assertTrue(store.tryStartExecution(request));
		assertFalse(store.tryStartExecution(request));
	}

	private ScheduledExecutionRequest request(String executionId) {
		SchedulerInfo info = new SchedulerInfo();
		info.setJobName("job1");
		info.setLauncher("local");
		ScheduledExecutionRequest request = new ScheduledExecutionRequest();
		request.setSchedulerInfo(info);
		request.setExecutionId(executionId);
		request.setScheduledFireTime(Instant.ofEpochMilli(1000));
		request.setActualFireTime(Instant.ofEpochMilli(1001));
		request.setAttempt(1);
		return request;
	}
}
