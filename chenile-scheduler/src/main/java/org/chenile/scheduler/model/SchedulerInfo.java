package org.chenile.scheduler.model;

import java.util.Map;

/**
 * Schedule definition.
 *
 */
public class SchedulerInfo {
	public String serviceName;
	public String operationName;
	public String cronSchedule;
	public String jobName;
	public String jobDescription;
	public String triggerName;
	private String triggerGroup;
	private String launcher;
	private String worker;
	private Integer retryCount;
	private Integer timeoutSeconds;
	private SchedulerConcurrencyPolicy concurrencyPolicy;
	private Map<String,String> jobLabels;
	private Map<String,String> jobAnnotations;
	
	public Map<String,Object> headers;
	public String payload;
	public String getJobName() {
		return jobName;
	}

	public void setJobName(String jobName) {
		this.jobName = jobName;
	}

	public String getJobDescription() {
		return jobDescription;
	}

	public void setJobDescription(String jobDescription) {
		this.jobDescription = jobDescription;
	}

	public String getTriggerName() {
		return triggerName;
	}

	public void setTriggerName(String triggerName) {
		this.triggerName = triggerName;
	}

	public String getTriggerGroup() {
		return triggerGroup;
	}

	public void setTriggerGroup(String triggerGroup) {
		this.triggerGroup = triggerGroup;
	}



	public String getCronSchedule() {
		return cronSchedule;
	}

	public void setCronSchedule(String cronSchedule) {
		this.cronSchedule = cronSchedule;
	}

	public Map<String,Object> getHeaders() {
		return headers;
	}

	public void setHeaders(Map<String,Object> headers) {
		this.headers = headers;
	}

	public String getLauncher() {
		return launcher;
	}

	public void setLauncher(String launcher) {
		this.launcher = normalize(launcher);
	}

	public void setBackend(String backend) {
		setLauncher(backend);
	}

	public String getBackend() {
		return launcher;
	}

	public String getWorker() {
		return worker;
	}

	public void setWorker(String worker) {
		this.worker = worker;
	}

	public Integer getRetryCount() {
		return retryCount;
	}

	public void setRetryCount(Integer retryCount) {
		this.retryCount = retryCount;
	}

	public Integer getTimeoutSeconds() {
		return timeoutSeconds;
	}

	public void setTimeoutSeconds(Integer timeoutSeconds) {
		this.timeoutSeconds = timeoutSeconds;
	}

	public SchedulerConcurrencyPolicy getConcurrencyPolicy() {
		return concurrencyPolicy;
	}

	public void setConcurrencyPolicy(SchedulerConcurrencyPolicy concurrencyPolicy) {
		this.concurrencyPolicy = concurrencyPolicy;
	}

	public void setConcurrencyPolicy(String concurrencyPolicy) {
		this.concurrencyPolicy = SchedulerConcurrencyPolicy.from(concurrencyPolicy);
	}

	public Map<String, String> getJobLabels() {
		return jobLabels;
	}

	public void setJobLabels(Map<String, String> jobLabels) {
		this.jobLabels = jobLabels;
	}

	public Map<String, String> getJobAnnotations() {
		return jobAnnotations;
	}

	public void setJobAnnotations(Map<String, String> jobAnnotations) {
		this.jobAnnotations = jobAnnotations;
	}

	private String normalize(String value) {
		return value == null ? null : value.trim().toLowerCase().replace('_', '-');
	}
}
