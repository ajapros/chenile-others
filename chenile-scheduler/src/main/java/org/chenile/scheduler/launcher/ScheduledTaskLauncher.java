package org.chenile.scheduler.launcher;

import org.chenile.scheduler.model.ScheduledExecutionRequest;

public interface ScheduledTaskLauncher {
	String launcherName();

	default boolean completesSynchronously() {
		return true;
	}

	LaunchResult launch(ScheduledExecutionRequest request) throws Exception;
}
