package org.chenile.scheduler.model;

import java.time.Instant;

public class ScheduledExecutionRecord {
	private String executionId;
	private String jobName;
	private Instant scheduledFireTime;
	private Instant actualFireTime;
	private Instant startedAt;
	private Instant finishedAt;
	private SchedulerExecutionStatus status;
	private int attempt;
	private Long durationMillis;
	private String errorMessage;
	private String launcher;
	private String launcherMetadata;

	public String getExecutionId() {
		return executionId;
	}

	public void setExecutionId(String executionId) {
		this.executionId = executionId;
	}

	public String getJobName() {
		return jobName;
	}

	public void setJobName(String jobName) {
		this.jobName = jobName;
	}

	public Instant getScheduledFireTime() {
		return scheduledFireTime;
	}

	public void setScheduledFireTime(Instant scheduledFireTime) {
		this.scheduledFireTime = scheduledFireTime;
	}

	public Instant getActualFireTime() {
		return actualFireTime;
	}

	public void setActualFireTime(Instant actualFireTime) {
		this.actualFireTime = actualFireTime;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public void setStartedAt(Instant startedAt) {
		this.startedAt = startedAt;
	}

	public Instant getFinishedAt() {
		return finishedAt;
	}

	public void setFinishedAt(Instant finishedAt) {
		this.finishedAt = finishedAt;
	}

	public SchedulerExecutionStatus getStatus() {
		return status;
	}

	public void setStatus(SchedulerExecutionStatus status) {
		this.status = status;
	}

	public int getAttempt() {
		return attempt;
	}

	public void setAttempt(int attempt) {
		this.attempt = attempt;
	}

	public Long getDurationMillis() {
		return durationMillis;
	}

	public void setDurationMillis(Long durationMillis) {
		this.durationMillis = durationMillis;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public String getLauncher() {
		return launcher;
	}

	public void setLauncher(String launcher) {
		this.launcher = launcher;
	}

	public String getLauncherMetadata() {
		return launcherMetadata;
	}

	public void setLauncherMetadata(String launcherMetadata) {
		this.launcherMetadata = launcherMetadata;
	}
}
