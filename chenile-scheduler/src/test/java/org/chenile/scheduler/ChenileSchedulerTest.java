package org.chenile.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.chenile.base.exception.ConfigurationException;
import org.chenile.trigger.TriggerHeaders;
import org.chenile.trigger.TriggerService;
import org.chenile.trigger.TriggerSources;
import org.chenile.trigger.model.TriggerInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.quartz.Scheduler;
import org.quartz.SchedulerContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

/** Startup checks and the trigger a fire produces, without a running Quartz. */
class ChenileSchedulerTest {
  private final Scheduler quartz = mock(Scheduler.class);
  private final TriggerService triggers = mock(TriggerService.class);

  @BeforeEach
  void quartzHasAContext() throws Exception {
    when(quartz.getContext()).thenReturn(new SchedulerContext());
  }

  @Test
  void twoJsonSchedulesWithTheSameIdFailStartup() {
    var scheduler =
        new ChenileScheduler(quartz, triggers, new Resource[] {json("same"), json("same")});
    assertThrows(ConfigurationException.class, scheduler::afterPropertiesSet);
  }

  @Test
  void aFireBecomesACronTriggerKeyedByTheScheduledTime() throws Exception {
    var scheduler = new ChenileScheduler(quartz, triggers, new Resource[] {json("nightly")});
    scheduler.afterPropertiesSet();
    Date scheduled = Date.from(Instant.parse("2026-01-01T02:00:00Z"));
    Date actual = Date.from(Instant.parse("2026-01-01T02:00:01Z"));
    scheduler.fire("nightly", scheduled, actual);

    ArgumentCaptor<TriggerInput> input = ArgumentCaptor.forClass(TriggerInput.class);
    verify(triggers).trigger(input.capture());
    assertEquals("tick", input.getValue().eventId);
    assertEquals(TriggerSources.CRON, input.getValue().source);
    assertEquals("schedule:nightly:2026-01-01T02:00:00Z", input.getValue().idempotencyKey);
    assertEquals("t1", input.getValue().headers.get(TriggerHeaders.TENANT_ID));
    assertEquals("nightly", input.getValue().headers.get(TriggerHeaders.SCHEDULE_ID));
    assertEquals(
        "2026-01-01T02:00:00Z", input.getValue().headers.get(TriggerHeaders.SCHEDULED_FIRE_TIME));
    assertEquals("2026-01-01T02:00:01Z", input.getValue().headers.get(TriggerHeaders.FIRE_TIME));
  }

  @Test
  void aFireForAnUnknownScheduleIsSkipped() throws Exception {
    var scheduler = new ChenileScheduler(quartz, triggers, new Resource[0]);
    scheduler.afterPropertiesSet();
    scheduler.fire("unknown", new Date(), new Date());
    verifyNoInteractions(triggers);
  }

  @Test
  void aFireReadsTheLookupFirstAndSkipsDisabledDefinitions() throws Exception {
    var scheduler = new ChenileScheduler(quartz, triggers, new Resource[] {json("nightly")});
    scheduler.afterPropertiesSet();
    SchedulerDefinition fresh = new SchedulerDefinition();
    fresh.id = "nightly";
    fresh.eventId = "changedEvent";
    scheduler.lookUpDefinitionsWith(id -> "nightly".equals(id) ? fresh : null);
    scheduler.fire("nightly", new Date(), new Date());
    ArgumentCaptor<TriggerInput> input = ArgumentCaptor.forClass(TriggerInput.class);
    verify(triggers).trigger(input.capture());
    assertEquals("changedEvent", input.getValue().eventId);

    fresh.enabled = false;
    scheduler.fire("nightly", new Date(), new Date());
    verify(triggers).trigger(any(TriggerInput.class));
  }

  private static Resource json(String id) {
    String text =
        "{\"id\":\"" + id + "\",\"cronExpression\":\"0 0 2 * * ?\",\"eventId\":\"tick\","
            + "\"tenant\":\"t1\",\"payload\":\"{}\"}";
    return new ByteArrayResource(text.getBytes(StandardCharsets.UTF_8), id + ".json");
  }
}
