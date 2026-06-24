package org.chenile.scheduler.test.support;

import io.fabric8.kubernetes.api.model.batch.v1.Job;
import org.chenile.scheduler.model.ScheduledExecutionRequest;

public interface KubernetesJobClient {
	String createJob(ScheduledExecutionRequest request, Job job);
}
