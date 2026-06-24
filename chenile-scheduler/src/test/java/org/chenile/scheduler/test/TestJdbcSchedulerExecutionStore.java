package org.chenile.scheduler.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Instant;

import org.chenile.scheduler.model.ScheduledExecutionRequest;
import org.chenile.scheduler.model.ScheduledExecutionRecord;
import org.chenile.scheduler.model.SchedulerExecutionStatus;
import org.chenile.scheduler.model.SchedulerInfo;
import org.chenile.scheduler.store.JdbcSchedulerExecutionStore;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

public class TestJdbcSchedulerExecutionStore {
	@Test public void storesExecutionLifecycle() {
		EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
				.setType(EmbeddedDatabaseType.H2)
				.addScript("chenile-scheduler-schema.sql")
				.build();
		try {
			JdbcSchedulerExecutionStore store = new JdbcSchedulerExecutionStore(new JdbcTemplate(database));
			ScheduledExecutionRequest request = request("job1-1000");
			assertTrue(store.tryStartExecution(request));
			assertFalse(store.tryStartExecution(request));

			store.markSuccess(request.getExecutionId(), 1, "local");
			ScheduledExecutionRecord record = store.findByExecutionId(request.getExecutionId()).orElseThrow();
			assertEquals(SchedulerExecutionStatus.SUCCESS, record.getStatus());
			assertEquals("local", record.getLauncherMetadata());
		} finally {
			database.shutdown();
		}
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
