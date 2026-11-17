package org.chenile.configuration.scheduler;


import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.chenile.core.entrypoint.ChenileEntryPoint;
import org.chenile.scheduler.launcher.LocalChenileTaskLauncher;
import org.chenile.scheduler.launcher.ScheduledTaskDispatcher;
import org.chenile.scheduler.launcher.ScheduledTaskLauncher;
import org.chenile.scheduler.init.ChenileSchedulerInitializer;
import org.chenile.scheduler.init.SchedulerBuilder;
import org.chenile.scheduler.service.SchedulerExecutionService;
import org.chenile.scheduler.store.JdbcSchedulerExecutionStore;
import org.chenile.scheduler.store.SchedulerExecutionStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@PropertySource("classpath:${chenile.properties:chenile.properties}")
public class ChenileSchedulerConfiguration {

	@Value("${chenile.scheduler.json.package:}")
	private Resource[] schedulerResources;
	@Value("${chenile.scheduler.launcher.default:${chenile.scheduler.backend.default:local}}")
	private String defaultLauncher;

	// The Quartz Scheduler bean ("quartzScheduler") comes from Spring Boot's Quartz auto-configuration, configured
	// through spring.quartz.* (in memory by default; JDBC and clustered by configuration).

	@Bean public SchedulerBuilder schedulerBuilder() {
		return new SchedulerBuilder();
	}

	@Bean public ChenileSchedulerInitializer chenileSchedulerInitializer(){
		return new ChenileSchedulerInitializer(schedulerResources);
	}

	@Bean public LocalChenileTaskLauncher localChenileTaskLauncher(ChenileEntryPoint chenileEntryPoint) {
		return new LocalChenileTaskLauncher(chenileEntryPoint);
	}

	@Bean public ScheduledTaskDispatcher scheduledTaskDispatcher(
			List<ScheduledTaskLauncher> launchers, SchedulerExecutionStore executionStore) {
		Map<String,ScheduledTaskLauncher> launcherMap = new HashMap<>();
		for (ScheduledTaskLauncher launcher : launchers) {
			launcherMap.put(launcher.launcherName(), launcher);
		}
		return new ScheduledTaskDispatcher(launcherMap, executionStore, defaultLauncher);
	}

	@Bean
	@ConditionalOnProperty(name = "chenile.scheduler.store.type", havingValue = "jdbc")
	public SchedulerExecutionStore jdbcSchedulerExecutionStore(JdbcTemplate jdbcTemplate) {
		return new JdbcSchedulerExecutionStore(jdbcTemplate);
	}

	@Bean public SchedulerExecutionService schedulerExecutionService(SchedulerExecutionStore executionStore) {
		return new SchedulerExecutionService(executionStore);
	}
	
}
