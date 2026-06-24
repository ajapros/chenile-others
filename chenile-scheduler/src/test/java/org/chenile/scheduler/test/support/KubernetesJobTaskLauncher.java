package org.chenile.scheduler.test.support;

import java.util.LinkedHashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import org.chenile.scheduler.launcher.LaunchResult;
import org.chenile.scheduler.launcher.ScheduledTaskLauncher;
import org.chenile.scheduler.model.ScheduledExecutionRequest;
import org.chenile.scheduler.model.SchedulerInfo;
import org.springframework.util.StringUtils;

public class KubernetesJobTaskLauncher implements ScheduledTaskLauncher {
	public static final String LAUNCHER_NAME = "kubernetes";
	private final KubernetesJobClient kubernetesJobClient;
	private final KubernetesJobProperties properties;

	public KubernetesJobTaskLauncher(KubernetesJobClient kubernetesJobClient,
			KubernetesJobProperties properties) {
		this.kubernetesJobClient = kubernetesJobClient;
		this.properties = properties;
	}

	@Override
	public String launcherName() {
		return LAUNCHER_NAME;
	}

	@Override
	public boolean completesSynchronously() {
		return false;
	}

	@Override
	public LaunchResult launch(ScheduledExecutionRequest request) throws Exception {
		String kubernetesJobName = kubernetesJobName(request);
		Job job = buildJob(request, kubernetesJobName);
		String metadata = kubernetesJobClient.createJob(request, job);
		return new LaunchResult(metadata == null ? kubernetesJobName : metadata);
	}

	public Job buildJob(ScheduledExecutionRequest request, String kubernetesJobName) {
		SchedulerInfo info = request.getSchedulerInfo();
		Map<String,String> labels = new LinkedHashMap<>();
		labels.put("app.kubernetes.io/managed-by", "chenile-scheduler");
		labels.put("chenile.scheduler/job-name", info.getJobName());
		labels.put("chenile.scheduler/execution-id", request.getExecutionId());
		if (info.getJobLabels() != null) {
			labels.putAll(info.getJobLabels());
		}

		JobBuilder builder = new JobBuilder()
				.withNewMetadata()
					.withName(kubernetesJobName)
					.withLabels(labels)
					.withAnnotations(info.getJobAnnotations())
				.endMetadata()
				.withNewSpec()
					.withBackoffLimit(effectiveRetryCount(info))
					.withNewTemplate()
						.withNewMetadata()
							.withLabels(labels)
						.endMetadata()
						.withNewSpec()
							.withRestartPolicy("Never")
							.addNewContainer()
								.withName("worker")
								.withImage(requiredWorkerImage())
								.withEnv(env(request))
							.endContainer()
						.endSpec()
					.endTemplate()
				.endSpec();
		if (info.getTimeoutSeconds() != null) {
			builder.editSpec().withActiveDeadlineSeconds((long)info.getTimeoutSeconds()).endSpec();
		}
		if (StringUtils.hasText(properties.getServiceAccount())) {
			builder.editSpec().editTemplate().editSpec()
					.withServiceAccountName(properties.getServiceAccount())
					.endSpec().endTemplate().endSpec();
		}
		return builder.build();
	}

	private String requiredWorkerImage() {
		if (!StringUtils.hasText(properties.getWorkerImage())) {
			throw new IllegalStateException("test Kubernetes worker image must be configured");
		}
		return properties.getWorkerImage();
	}

	private java.util.List<EnvVar> env(ScheduledExecutionRequest request) {
		SchedulerInfo info = request.getSchedulerInfo();
		return java.util.List.of(
				env("CHENILE_SCHEDULER_EXECUTION_ID", request.getExecutionId()),
				env("CHENILE_SCHEDULER_JOB_NAME", info.getJobName()),
				env("CHENILE_SCHEDULER_WORKER", nullToEmpty(info.getWorker())),
				env("CHENILE_SCHEDULER_PAYLOAD", nullToEmpty(info.payload)),
				env("CHENILE_SCHEDULER_SERVICE_NAME", nullToEmpty(info.serviceName)),
				env("CHENILE_SCHEDULER_OPERATION_NAME", nullToEmpty(info.operationName)));
	}

	private EnvVar env(String name, String value) {
		return new EnvVarBuilder().withName(name).withValue(value).build();
	}

	private String kubernetesJobName(ScheduledExecutionRequest request) {
		String base = request.getJobName().toLowerCase().replaceAll("[^a-z0-9-]", "-");
		String suffix = Integer.toHexString(request.getExecutionId().hashCode());
		String name = base + "-" + suffix;
		return name.length() <= 63 ? name : name.substring(0, 63);
	}

	private int effectiveRetryCount(SchedulerInfo info) {
		return info.getRetryCount() == null ? 0 : info.getRetryCount();
	}

	private String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}
