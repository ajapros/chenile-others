package org.chenile.scheduler.launcher;

public class LaunchResult {
	private String launcherMetadata;

	public LaunchResult() {
	}

	public LaunchResult(String launcherMetadata) {
		this.launcherMetadata = launcherMetadata;
	}

	public String getLauncherMetadata() {
		return launcherMetadata;
	}

	public void setLauncherMetadata(String launcherMetadata) {
		this.launcherMetadata = launcherMetadata;
	}
}
