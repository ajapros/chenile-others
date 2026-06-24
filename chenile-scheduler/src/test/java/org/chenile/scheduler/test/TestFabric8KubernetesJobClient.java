package org.chenile.scheduler.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.time.Instant;
import java.util.Date;
import java.util.Map;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.chenile.scheduler.launcher.ScheduledTaskDispatcher;
import org.chenile.scheduler.model.ScheduledExecutionRecord;
import org.chenile.scheduler.model.SchedulerExecutionStatus;
import org.chenile.scheduler.model.SchedulerInfo;
import org.chenile.scheduler.test.support.Fabric8KubernetesJobClient;
import org.chenile.scheduler.test.support.KubernetesJobProperties;
import org.chenile.scheduler.test.support.KubernetesJobTaskLauncher;
import org.chenile.scheduler.test.support.MemorySchedulerExecutionStore;
import org.junit.Assume;
import org.junit.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;

public class TestFabric8KubernetesJobClient {
	@Test public void createsJobInK3sCluster() throws Exception {
		Assume.assumeTrue("Docker is required for the K3s scheduler integration test",
				DockerClientFactory.instance().isDockerAvailable());
		try (K3sContainer k3s = new K3sContainer(DockerImageName.parse("rancher/k3s:v1.33.1-k3s1"))) {
			k3s.start();
			Config config = Config.fromKubeconfig(k3s.getKubeConfigYaml());
			try (KubernetesClient kubernetesClient = new KubernetesClientBuilder().withConfig(config).build()) {
				KubernetesJobProperties properties = new KubernetesJobProperties();
				properties.setNamespace("default");
				properties.setWorkerImage("busybox:1.36");
				properties.setServiceAccount("default");
				KubernetesJobTaskLauncher launcher = new KubernetesJobTaskLauncher(
						new Fabric8KubernetesJobClient(kubernetesClient, properties), properties);
				MemorySchedulerExecutionStore store = new MemorySchedulerExecutionStore();
				ScheduledTaskDispatcher dispatcher = new ScheduledTaskDispatcher(
						Map.of(launcher.launcherName(), launcher), store, "kubernetes");

				SchedulerInfo schedulerInfo = schedulerInfo();
				dispatcher.dispatch(schedulerInfo, null, null,
						Date.from(Instant.ofEpochMilli(1000)), Date.from(Instant.ofEpochMilli(1001)));
				ScheduledExecutionRecord record = store.findByExecutionId("daily-report-1000").orElseThrow();
				String jobName = record.getLauncherMetadata();

				Job job = kubernetesClient.batch().v1().jobs()
						.inNamespace("default")
						.withName(jobName)
						.get();
				assertNotNull(job);
				assertEquals(SchedulerExecutionStatus.SUCCESS, record.getStatus());
				assertEquals("kubernetes", record.getLauncher());
				assertEquals("daily-report", job.getMetadata().getLabels().get("chenile.scheduler/job-name"));
				assertEquals("reports", job.getMetadata().getLabels().get("team"));
				assertEquals("integration", job.getMetadata().getAnnotations().get("purpose"));
				assertEquals(Integer.valueOf(3), job.getSpec().getBackoffLimit());
				assertEquals(Long.valueOf(300), job.getSpec().getActiveDeadlineSeconds());
				assertEquals("default", job.getSpec().getTemplate().getSpec().getServiceAccountName());
				Container container = job.getSpec().getTemplate().getSpec().getContainers().getFirst();
				assertEquals("busybox:1.36", container.getImage());
				assertEquals("daily-report", env(container, "CHENILE_SCHEDULER_JOB_NAME"));
				assertEquals("report-worker", env(container, "CHENILE_SCHEDULER_WORKER"));
				assertEquals("{ \"report_type\": \"daily\" }", env(container, "CHENILE_SCHEDULER_PAYLOAD"));
			}
		}
	}

	private SchedulerInfo schedulerInfo() {
		SchedulerInfo info = new SchedulerInfo();
		info.setJobName("daily-report");
		info.setLauncher("kubernetes");
		info.setWorker("report-worker");
		info.serviceName = "reportService";
		info.operationName = "run";
		info.payload = "{ \"report_type\": \"daily\" }";
		info.setRetryCount(3);
		info.setTimeoutSeconds(300);
		info.setJobLabels(Map.of("team", "reports"));
		info.setJobAnnotations(Map.of("purpose", "integration"));
		return info;
	}

	private String env(Container container, String name) {
		return container.getEnv().stream()
				.filter(envVar -> name.equals(envVar.getName()))
				.map(EnvVar::getValue)
				.findFirst()
				.orElse(null);
	}
}
