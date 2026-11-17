package org.chenile.scheduler.bdd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.spring.CucumberContextConfiguration;
import java.util.List;
import java.util.Set;
import org.chenile.base.exception.BadRequestException;
import org.chenile.scheduler.ChenileScheduler;
import org.chenile.scheduler.SchedulerDefinition;
import org.chenile.scheduler.errorcodes.ErrorCodes;
import org.chenile.scheduler.test.SchedulerTestApplication;
import org.chenile.scheduler.test.service.TickListener;
import org.chenile.scheduler.test.service.TickListener.Delivery;
import org.quartz.CronTrigger;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@CucumberContextConfiguration
@SpringBootTest(classes = SchedulerTestApplication.class)
public class SchedulerSteps {
  private static final List<String> CODED = List.of("coded", "nightly", "bad");

  @Autowired ChenileScheduler scheduler;
  @Autowired Scheduler quartz;
  @Autowired TickListener listener;

  private Delivery delivery;
  private RuntimeException failure;

  @Before
  public void reset() {
    listener.deliveries.clear();
    delivery = null;
    failure = null;
  }

  @After
  public void removeCodedSchedules() {
    CODED.forEach(scheduler::unschedule);
  }

  @When("code schedules {string} with cron {string} and note {string}")
  public void schedule(String id, String cron, String note) {
    SchedulerDefinition definition = new SchedulerDefinition();
    definition.id = id;
    definition.cronExpression = cron;
    definition.eventId = "tick";
    definition.payload = "{\"note\": \"" + note + "\"}";
    try {
      scheduler.schedule(definition);
    } catch (RuntimeException e) {
      failure = e;
    }
  }

  @When("code disables schedule {string}")
  public void disable(String id) {
    SchedulerDefinition definition = scheduler.definition(id);
    definition.enabled = false;
    scheduler.schedule(definition);
  }

  @Then("within {int} seconds the listener receives a tick with note {string}")
  public void receives(int seconds, String note) throws InterruptedException {
    long deadline = System.currentTimeMillis() + seconds * 1000L;
    while (delivery == null && System.currentTimeMillis() < deadline) {
      delivery =
          listener.deliveries.stream().filter(d -> note.equals(d.note())).findFirst().orElse(null);
      if (delivery == null) Thread.sleep(100);
    }
    assertNotNull(delivery, "no tick with note " + note);
  }

  @Then("that tick has header {string} equal to {string}")
  public void header(String name, String value) {
    assertEquals(value, delivery.headers().get(name));
  }

  @Then("that tick has headers {string}")
  public void headersPresent(String names) {
    for (String name : names.split(","))
      assertNotNull(delivery.headers().get(name), "missing header " + name);
  }

  @Then("the scheduler knows schedule {string}")
  public void known(String id) {
    assertNotNull(scheduler.definition(id));
  }

  @Then("Quartz has no job for schedule {string}")
  public void noJob(String id) throws Exception {
    assertFalse(quartz.checkExists(new JobKey(id, ChenileScheduler.GROUP)));
  }

  @Then("Quartz stores only the schedule id in the job data of {string}")
  public void jobData(String id) throws Exception {
    var data = quartz.getJobDetail(new JobKey(id, ChenileScheduler.GROUP)).getJobDataMap();
    assertEquals(Set.of(ChenileScheduler.DEFINITION_ID), data.keySet());
    assertEquals(id, data.getString(ChenileScheduler.DEFINITION_ID));
  }

  @Then("Quartz has one trigger for schedule {string} with cron {string}")
  public void oneTrigger(String id, String cron) throws Exception {
    List<? extends Trigger> triggers =
        quartz.getTriggersOfJob(new JobKey(id, ChenileScheduler.GROUP));
    assertEquals(1, triggers.size());
    assertTrue(triggers.get(0) instanceof CronTrigger);
    assertEquals(cron, ((CronTrigger) triggers.get(0)).getCronExpression());
  }

  @Then("the schedule is refused with {string}")
  public void refused(String reason) {
    assertTrue(failure instanceof BadRequestException, String.valueOf(failure));
    BadRequestException refused = (BadRequestException) failure;
    assertEquals(
        String.valueOf(ErrorCodes.INVALID_SCHEDULE.getSubError()), refused.getSubErrorNum());
    assertTrue(String.valueOf(refused.getParams()[1]).contains(reason));
  }
}
