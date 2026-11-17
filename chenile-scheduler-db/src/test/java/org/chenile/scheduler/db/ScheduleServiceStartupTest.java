package org.chenile.scheduler.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.chenile.base.exception.ConfigurationException;
import org.chenile.scheduler.ChenileScheduler;
import org.chenile.scheduler.SchedulerDefinition;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** What happens when a node starts with schedules already in the database. */
class ScheduleServiceStartupTest {
  private final SchedulerDefinitionRepository repository = mock(SchedulerDefinitionRepository.class);
  private final ChenileScheduler scheduler = mock(ChenileScheduler.class);
  private final ScheduleService service = new ScheduleService(repository, scheduler);

  @Test
  void storedSchedulesAreScheduledAndFiresReadTheDatabase() {
    SchedulerDefinitionEntity stored = entity("schedule-1");
    when(repository.findAll()).thenReturn(List.of(stored));
    when(repository.findById("schedule-1")).thenReturn(Optional.of(stored));

    service.afterSingletonsInstantiated();

    ArgumentCaptor<SchedulerDefinition> scheduled = ArgumentCaptor.forClass(SchedulerDefinition.class);
    verify(scheduler).schedule(scheduled.capture());
    assertEquals("schedule-1", scheduled.getValue().id);
    assertEquals("alpha", scheduled.getValue().tenant);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Function<String, SchedulerDefinition>> lookup = ArgumentCaptor.forClass(Function.class);
    verify(scheduler).lookUpDefinitionsWith(lookup.capture());
    assertEquals("customerEvent", lookup.getValue().apply("schedule-1").eventId);
    assertEquals(null, lookup.getValue().apply("unknown"));
  }

  @Test
  void aStoredIdAlreadyDefinedInJsonFailsStartup() {
    when(repository.findAll()).thenReturn(List.of(entity("nightly")));
    when(scheduler.definition("nightly")).thenReturn(new SchedulerDefinition());
    assertThrows(ConfigurationException.class, service::afterSingletonsInstantiated);
    verify(scheduler, never()).schedule(any());
  }

  private static SchedulerDefinitionEntity entity(String id) {
    SchedulerDefinitionEntity entity = new SchedulerDefinitionEntity();
    entity.id = id;
    entity.name = id;
    entity.tenant = "alpha";
    entity.cronExpression = "0 0 2 * * ?";
    entity.eventId = "customerEvent";
    return entity;
  }
}
