package org.chenile.scheduler.test;

import static org.junit.Assert.assertEquals;

import org.chenile.scheduler.model.SchedulerInfo;
import org.junit.Test;

import tools.jackson.databind.ObjectMapper;

public class TestSchedulerInfoJsonParsing {
	@Test public void parsesEnhancedSchedulerJson() throws Exception {
		String json = """
				{
				  "serviceName": "reportService",
				  "operationName": "run",
				  "cronSchedule": "0 2 * * * ?",
				  "jobName": "daily-report",
				  "launcher": "kubernetes",
				  "worker": "report-worker",
				  "retryCount": 3,
				  "timeoutSeconds": 300
				}
				""";
		SchedulerInfo info = new ObjectMapper().readValue(json, SchedulerInfo.class);
		assertEquals("kubernetes", info.getLauncher());
		assertEquals("report-worker", info.getWorker());
		assertEquals(Integer.valueOf(3), info.getRetryCount());
		assertEquals(Integer.valueOf(300), info.getTimeoutSeconds());
	}

	@Test public void supportsLegacyBackendJsonAlias() throws Exception {
		SchedulerInfo info = new ObjectMapper().readValue("""
				{
				  "jobName": "daily-report",
				  "backend": "kubernetes"
				}
				""", SchedulerInfo.class);
		assertEquals("kubernetes", info.getLauncher());
	}
}
