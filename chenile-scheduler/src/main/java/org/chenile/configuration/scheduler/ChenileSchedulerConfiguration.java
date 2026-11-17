package org.chenile.configuration.scheduler;

import org.chenile.scheduler.ChenileScheduler;
import org.chenile.trigger.TriggerService;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.io.Resource;

/**
 * The Quartz {@link Scheduler} ("quartzScheduler") comes from Spring Boot's Quartz
 * auto-configuration, set up through {@code spring.quartz.*}: in memory by default, JDBC and
 * clustered by configuration. The {@link TriggerService} is chenile-trigger's
 * DirectTriggerService unless process management supplies its durable one.
 */
@Configuration
@PropertySource("classpath:${chenile.properties:chenile.properties}")
public class ChenileSchedulerConfiguration {
  @Bean
  public ChenileScheduler chenileScheduler(
      Scheduler quartzScheduler,
      TriggerService triggerService,
      @Value("${chenile.scheduler.json.package:}") Resource[] scheduleResources) {
    return new ChenileScheduler(quartzScheduler, triggerService, scheduleResources);
  }
}
