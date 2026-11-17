package org.chenile.scheduler.test;

import org.chenile.scheduler.test.service.TickListener;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;

@SpringBootApplication(scanBasePackages = "org.chenile.configuration")
@PropertySource("classpath:org/chenile/scheduler/test/TestChenileScheduler.properties")
public class SchedulerTestApplication {
  @Bean
  public TickListener tickListener() {
    return new TickListener();
  }
}
