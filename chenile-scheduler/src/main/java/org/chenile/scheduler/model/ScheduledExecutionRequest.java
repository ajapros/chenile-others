package org.chenile.scheduler.model;

import java.time.Instant;
import java.util.Map;

import org.chenile.core.model.ChenileServiceDefinition;
import org.chenile.core.model.OperationDefinition;

public class ScheduledExecutionRequest {
	private SchedulerInfo schedulerInfo;
	private ChenileServiceDefinition serviceDefinition;
	private OperationDefinition operationDefinition;
	private String executionId;
	private Instant scheduledFireTime;
	private Instant actualFireTime;
	private int attempt;

	public SchedulerInfo getSchedulerInfo() {
		return schedulerInfo;
	}

	public void setSchedulerInfo(SchedulerInfo schedulerInfo) {
		this.schedulerInfo = schedulerInfo;
	}

	public ChenileServiceDefinition getServiceDefinition() {
		return serviceDefinition;
	}

	public void setServiceDefinition(ChenileServiceDefinition serviceDefinition) {
		this.serviceDefinition = serviceDefinition;
	}

	public OperationDefinition getOperationDefinition() {
		return operationDefinition;
	}

	public void setOperationDefinition(OperationDefinition operationDefinition) {
		this.operationDefinition = operationDefinition;
	}

	public String getExecutionId() {
		return executionId;
	}

	public void setExecutionId(String executionId) {
		this.executionId = executionId;
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

	public int getAttempt() {
		return attempt;
	}

	public void setAttempt(int attempt) {
		this.attempt = attempt;
	}

	public String getJobName() {
		return schedulerInfo.getJobName();
	}

	public String getPayload() {
		return schedulerInfo.payload;
	}

	public Map<String,Object> getHeaders() {
		return schedulerInfo.getHeaders();
	}

	public String getLauncher() {
		return schedulerInfo.getLauncher();
	}
}
