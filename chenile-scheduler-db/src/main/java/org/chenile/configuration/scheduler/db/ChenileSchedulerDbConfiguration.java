package org.chenile.configuration.scheduler.db;

import org.chenile.configuration.scheduler.db.controller.ScheduleController;
import org.chenile.scheduler.ChenileScheduler;
import org.chenile.scheduler.db.ScheduleService;
import org.chenile.scheduler.db.SchedulerDefinitionRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * The host application discovers the entity and repository (package {@code
 * org.chenile.scheduler.db}) with its own {@code @EntityScan} and {@code @EnableJpaRepositories};
 * a library never scans persistence.
 */
@Configuration
@Import(ScheduleController.class)
public class ChenileSchedulerDbConfiguration {
  @Bean(name = {"_scheduleService_", "scheduleService"})
  public ScheduleService scheduleService(
      SchedulerDefinitionRepository repository, ChenileScheduler chenileScheduler) {
    return new ScheduleService(repository, chenileScheduler);
  }
}
