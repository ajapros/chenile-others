package org.chenile.scheduler.db.bdd;

import org.chenile.scheduler.db.SchedulerDefinitionEntity;
import org.chenile.scheduler.db.SchedulerDefinitionRepository;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** The host owns persistence discovery; the library never scans entities or repositories. */
@SpringBootApplication(scanBasePackages = "org.chenile.configuration")
@EntityScan(basePackageClasses = SchedulerDefinitionEntity.class)
@EnableJpaRepositories(basePackageClasses = SchedulerDefinitionRepository.class)
public class SchedulerDbTestApplication {}
