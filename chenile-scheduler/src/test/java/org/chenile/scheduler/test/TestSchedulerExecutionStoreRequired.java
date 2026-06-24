package org.chenile.scheduler.test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.List;

import org.chenile.scheduler.launcher.LaunchResult;
import org.chenile.scheduler.launcher.ScheduledTaskDispatcher;
import org.chenile.scheduler.launcher.ScheduledTaskLauncher;
import org.chenile.scheduler.model.ScheduledExecutionRequest;
import org.chenile.scheduler.store.SchedulerExecutionStore;
import org.junit.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public class TestSchedulerExecutionStoreRequired {
	@Test public void contextRequiresExecutionStoreBean() {
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
			context.register(MinimalSchedulerConfig.class);
			try {
				context.refresh();
				fail("Expected scheduler configuration to require a SchedulerExecutionStore bean");
			} catch (UnsatisfiedDependencyException e) {
				assertTrue(containsMissingStore(e));
			}
		}
	}

	private boolean containsMissingStore(Throwable throwable) {
		Throwable current = throwable;
		while (current != null) {
			if (current instanceof NoSuchBeanDefinitionException
					&& ((NoSuchBeanDefinitionException)current).getBeanType() == SchedulerExecutionStore.class) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	@Configuration
	static class MinimalSchedulerConfig {
		@Bean public ScheduledTaskLauncher scheduledTaskLauncher() {
			return new ScheduledTaskLauncher() {
				@Override
				public String launcherName() {
					return "test";
				}

				@Override
				public LaunchResult launch(ScheduledExecutionRequest request) {
					return new LaunchResult("test");
				}
			};
		}

		@Bean public ScheduledTaskDispatcher scheduledTaskDispatcher(
				List<ScheduledTaskLauncher> launchers, SchedulerExecutionStore executionStore) {
			HashMap<String,ScheduledTaskLauncher> launcherMap = new HashMap<>();
			for (ScheduledTaskLauncher launcher : launchers) {
				launcherMap.put(launcher.launcherName(), launcher);
			}
			return new ScheduledTaskDispatcher(launcherMap, executionStore, "test");
		}
	}
}
