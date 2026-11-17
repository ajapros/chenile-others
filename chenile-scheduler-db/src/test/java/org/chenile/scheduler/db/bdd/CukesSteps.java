package org.chenile.scheduler.db.bdd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.spring.CucumberContextConfiguration;
import java.util.Set;
import org.chenile.core.context.ContextContainer;
import org.chenile.cucumber.VariableHelper;
import org.chenile.scheduler.ChenileScheduler;
import org.chenile.scheduler.db.SchedulerDefinitionRepository;
import org.quartz.CronTrigger;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;

@CucumberContextConfiguration
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = SchedulerDbTestApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("unittest")
public class CukesSteps {
  @Autowired SchedulerDefinitionRepository repository;
  @Autowired ChenileScheduler scheduler;
  @Autowired Scheduler quartz;

  @Before
  public void resetData() {
    ContextContainer.getInstance().clear();
    repository.findAll().forEach(schedule -> scheduler.unschedule(schedule.id));
    repository.deleteAll();
  }

  @Given("dummy")
  public void dummy() {}

  @Then("Quartz has scheduled the saved schedule with cron {string}")
  public void scheduled(String cron) throws Exception {
    JobKey key = new JobKey(id(), ChenileScheduler.GROUP);
    assertTrue(quartz.checkExists(key));
    var triggers = quartz.getTriggersOfJob(key);
    assertEquals(1, triggers.size());
    assertEquals(cron, ((CronTrigger) triggers.get(0)).getCronExpression());
  }

  @Then("Quartz stores only the schedule id in the job data")
  public void jobData() throws Exception {
    var data = quartz.getJobDetail(new JobKey(id(), ChenileScheduler.GROUP)).getJobDataMap();
    assertEquals(Set.of(ChenileScheduler.DEFINITION_ID), data.keySet());
    assertEquals(id(), data.getString(ChenileScheduler.DEFINITION_ID));
  }

  @Then("the scheduler fires the saved schedule as tenant {string}")
  public void firesAsTenant(String tenant) {
    var definition = scheduler.definition(id());
    assertNotNull(definition);
    assertEquals(tenant, definition.tenant);
  }

  @Then("Quartz has removed the saved schedule")
  public void removed() throws Exception {
    assertFalse(quartz.checkExists(new JobKey(id(), ChenileScheduler.GROUP)));
  }

  @Then("the scheduler has forgotten the saved schedule")
  public void forgotten() {
    assertNull(scheduler.definition(id()));
  }

  @Then("Quartz has no schedules")
  public void noSchedules() throws Exception {
    assertTrue(quartz.getJobKeys(GroupMatcher.jobGroupEquals(ChenileScheduler.GROUP)).isEmpty());
  }

  private String id() {
    return VariableHelper.substituteVariables("${id}");
  }
}
