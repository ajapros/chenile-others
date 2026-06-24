package org.chenile.scheduler.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.chenile.scheduler.launcher.LaunchResult;
import org.chenile.scheduler.launcher.ScheduledTaskDispatcher;
import org.chenile.scheduler.launcher.ScheduledTaskLauncher;
import org.chenile.scheduler.model.ScheduledExecutionRequest;
import org.chenile.scheduler.model.ScheduledExecutionRecord;
import org.chenile.scheduler.model.SchedulerExecutionStatus;
import org.chenile.scheduler.model.SchedulerInfo;
import org.chenile.scheduler.test.support.MemorySchedulerExecutionStore;
import org.junit.Test;

public class TestScheduledTaskDispatcher {
	@Test public void retriesLocalLaunchUntilSuccess() throws Exception {
		MemorySchedulerExecutionStore store = new MemorySchedulerExecutionStore();
		CountingLauncher launcher = new CountingLauncher(2, 0);
		ScheduledTaskDispatcher dispatcher = dispatcher(store, launcher);
		SchedulerInfo info = info("retry-job");
		info.setRetryCount(2);

		dispatcher.dispatch(info, null, null, fireTime(), fireTime());

		ScheduledExecutionRecord record = store.findByExecutionId("retry-job-1000").orElseThrow();
		assertEquals(3, launcher.count);
		assertEquals(3, record.getAttempt());
		assertEquals(SchedulerExecutionStatus.SUCCESS, record.getStatus());
	}

	@Test public void skipsDuplicateFireTime() throws Exception {
		MemorySchedulerExecutionStore store = new MemorySchedulerExecutionStore();
		CountingLauncher launcher = new CountingLauncher(0, 0);
		ScheduledTaskDispatcher dispatcher = dispatcher(store, launcher);
		SchedulerInfo info = info("duplicate-job");

		dispatcher.dispatch(info, null, null, fireTime(), fireTime());
		dispatcher.dispatch(info, null, null, fireTime(), fireTime());

		assertEquals(1, launcher.count);
	}

	@Test public void marksTimedOutExecution() throws Exception {
		MemorySchedulerExecutionStore store = new MemorySchedulerExecutionStore();
		CountingLauncher launcher = new CountingLauncher(0, 2000);
		ScheduledTaskDispatcher dispatcher = dispatcher(store, launcher);
		SchedulerInfo info = info("timeout-job");
		info.setTimeoutSeconds(1);

		assertThrows(TimeoutException.class, () -> dispatcher.dispatch(info, null, null, fireTime(), fireTime()));

		ScheduledExecutionRecord record = store.findByExecutionId("timeout-job-1000").orElseThrow();
		assertEquals(SchedulerExecutionStatus.TIMED_OUT, record.getStatus());
		assertEquals(1, record.getAttempt());
	}

	@Test public void marksFailedAfterRetryExhaustion() throws Exception {
		MemorySchedulerExecutionStore store = new MemorySchedulerExecutionStore();
		CountingLauncher launcher = new CountingLauncher(3, 0);
		ScheduledTaskDispatcher dispatcher = dispatcher(store, launcher);
		SchedulerInfo info = info("failed-job");
		info.setRetryCount(2);

		assertThrows(IllegalStateException.class, () -> dispatcher.dispatch(info, null, null, fireTime(), fireTime()));

		ScheduledExecutionRecord record = store.findByExecutionId("failed-job-1000").orElseThrow();
		assertEquals(3, launcher.count);
		assertEquals(3, record.getAttempt());
		assertEquals(SchedulerExecutionStatus.FAILED, record.getStatus());
		assertEquals("failure 3", record.getErrorMessage());
	}

	@Test public void marksFailedWhenLauncherIsMissing() throws Exception {
		MemorySchedulerExecutionStore store = new MemorySchedulerExecutionStore();
		ScheduledTaskDispatcher dispatcher = new ScheduledTaskDispatcher(new HashMap<>(), store, "missing");
		SchedulerInfo info = info("missing-launcher-job");
		info.setLauncher("missing");

		assertThrows(IllegalStateException.class, () -> dispatcher.dispatch(info, null, null, fireTime(), fireTime()));

		ScheduledExecutionRecord record = store.findByExecutionId("missing-launcher-job-1000").orElseThrow();
		assertEquals(SchedulerExecutionStatus.FAILED, record.getStatus());
		assertEquals("No scheduler launcher configured for missing", record.getErrorMessage());
	}

	private ScheduledTaskDispatcher dispatcher(MemorySchedulerExecutionStore store, ScheduledTaskLauncher launcher) {
		Map<String,ScheduledTaskLauncher> launchers = new HashMap<>();
		launchers.put("test", launcher);
		return new ScheduledTaskDispatcher(launchers, store, "test");
	}

	private SchedulerInfo info(String jobName) {
		SchedulerInfo info = new SchedulerInfo();
		info.setJobName(jobName);
		info.setLauncher("test");
		return info;
	}

	private Date fireTime() {
		return Date.from(Instant.ofEpochMilli(1000));
	}

	private static class CountingLauncher implements ScheduledTaskLauncher {
		private final int failuresBeforeSuccess;
		private final long sleepMillis;
		private int count;

		private CountingLauncher(int failuresBeforeSuccess, long sleepMillis) {
			this.failuresBeforeSuccess = failuresBeforeSuccess;
			this.sleepMillis = sleepMillis;
		}

		@Override
		public String launcherName() {
			return "test";
		}

		@Override
		public LaunchResult launch(ScheduledExecutionRequest request) throws Exception {
			count++;
			if (sleepMillis > 0) {
				Thread.sleep(sleepMillis);
			}
			if (count <= failuresBeforeSuccess) {
				throw new IllegalStateException("failure " + count);
			}
			return new LaunchResult("attempt-" + count);
		}
	}
}
