package org.chenile.scheduler.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.time.Instant;

import io.fabric8.kubernetes.api.model.batch.v1.Job;
import org.chenile.scheduler.model.ScheduledExecutionRequest;
import org.chenile.scheduler.model.SchedulerInfo;
import org.chenile.scheduler.test.support.KubernetesJobClient;
import org.chenile.scheduler.test.support.KubernetesJobProperties;
import org.chenile.scheduler.test.support.KubernetesJobTaskLauncher;
import org.junit.Test;

public class TestKubernetesJobTaskLauncher {
	@Test public void createsKubernetesJobSpec() throws Exception {
		CapturingKubernetesJobClient client = new CapturingKubernetesJobClient();
		KubernetesJobProperties properties = new KubernetesJobProperties();
		properties.setWorkerImage("example/report-worker:1");
		properties.setNamespace("jobs");
		KubernetesJobTaskLauncher launcher = new KubernetesJobTaskLauncher(client, properties);

		launcher.launch(request());

		assertNotNull(client.job);
		assertEquals("batch/v1", client.job.getApiVersion());
		assertEquals(Integer.valueOf(3), client.job.getSpec().getBackoffLimit());
		assertEquals(Long.valueOf(300), client.job.getSpec().getActiveDeadlineSeconds());
		assertEquals("Never", client.job.getSpec().getTemplate().getSpec().getRestartPolicy());
		assertEquals("example/report-worker:1",
				client.job.getSpec().getTemplate().getSpec().getContainers().getFirst().getImage());
	}

	private ScheduledExecutionRequest request() {
		SchedulerInfo info = new SchedulerInfo();
		info.setJobName("daily-report");
		info.setLauncher("kubernetes");
		info.setWorker("report-worker");
		info.serviceName = "reportService";
		info.operationName = "run";
		info.payload = "{ \"report_type\": \"daily\" }";
		info.setRetryCount(3);
		info.setTimeoutSeconds(300);
		ScheduledExecutionRequest request = new ScheduledExecutionRequest();
		request.setSchedulerInfo(info);
		request.setExecutionId("daily-report-1000");
		request.setScheduledFireTime(Instant.ofEpochMilli(1000));
		request.setActualFireTime(Instant.ofEpochMilli(1001));
		request.setAttempt(1);
		return request;
	}

	private static class CapturingKubernetesJobClient implements KubernetesJobClient {
		Job job;

		@Override
		public String createJob(ScheduledExecutionRequest request, Job job) {
			this.job = job;
			return "daily-report-test";
		}
	}
}
