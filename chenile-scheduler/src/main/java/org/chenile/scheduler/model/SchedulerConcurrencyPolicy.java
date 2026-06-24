package org.chenile.scheduler.model;

public enum SchedulerConcurrencyPolicy {
	ALLOW,
	FORBID;

	public static SchedulerConcurrencyPolicy from(String value) {
		return value == null ? null : SchedulerConcurrencyPolicy.valueOf(value.trim().toUpperCase().replace('-', '_'));
	}
}
