package org.chenile.scheduler.test.support;

import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.chenile.scheduler.model.ScheduledExecutionRequest;

public class Fabric8KubernetesJobClient implements KubernetesJobClient {
	private final KubernetesClient kubernetesClient;
	private final KubernetesJobProperties properties;

	public Fabric8KubernetesJobClient(KubernetesClient kubernetesClient, KubernetesJobProperties properties) {
		this.kubernetesClient = kubernetesClient;
		this.properties = properties;
	}

	@Override
	public String createJob(ScheduledExecutionRequest request, Job job) {
		Job created = kubernetesClient.batch().v1().jobs()
				.inNamespace(properties.getNamespace())
				.resource(job)
				.create();
		return created.getMetadata().getName();
	}
}
