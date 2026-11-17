package org.chenile.scheduler.db;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import org.chenile.base.exception.BadRequestException;
import org.chenile.base.exception.ConfigurationException;
import org.chenile.base.exception.NotFoundException;
import org.chenile.core.context.ContextContainer;
import org.chenile.scheduler.ChenileScheduler;
import org.chenile.scheduler.SchedulerDefinition;
import org.chenile.scheduler.errorcodes.ErrorCodes;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Saves, reads and deletes the caller tenant's schedules, and keeps {@link ChenileScheduler} in
 * step: a change is scheduled after its transaction commits. At startup every stored schedule is
 * scheduled, and the scheduler is told to read definitions from the database when they fire, so
 * that a schedule saved on one node fires correctly on another.
 */
public class ScheduleService implements SmartInitializingSingleton {
  private final SchedulerDefinitionRepository repository;
  private final ChenileScheduler scheduler;

  public ScheduleService(SchedulerDefinitionRepository repository, ChenileScheduler scheduler) {
    this.repository = repository;
    this.scheduler = scheduler;
  }

  @Override
  public void afterSingletonsInstantiated() {
    scheduler.lookUpDefinitionsWith(
        id -> repository.findById(id).map(this::toDefinition).orElse(null));
    for (SchedulerDefinitionEntity entity : repository.findAll()) {
      if (scheduler.definition(entity.id) != null)
        throw new ConfigurationException(
            String.valueOf(ErrorCodes.DUPLICATE_SCHEDULE_ID.getSubError()),
            new Object[] {entity.id});
      scheduler.schedule(toDefinition(entity));
    }
  }

  @Transactional
  public SchedulerDefinitionEntity save(SchedulerDefinitionEntity schedule) {
    validate(schedule);
    if (schedule.tenant != null && !Objects.equals(schedule.tenant, tenant()))
      throw new BadRequestException(
          String.valueOf(ErrorCodes.SCHEDULE_TENANT_MISMATCH.getSubError()), new Object[] {});
    if (schedule.id != null) {
      var existing = repository.findById(schedule.id);
      if (existing.isPresent() && !Objects.equals(existing.get().tenant, tenant()))
        throw notFound(schedule.id);
      if (existing.isEmpty() && scheduler.definition(schedule.id) != null)
        throw new BadRequestException(
            String.valueOf(ErrorCodes.DUPLICATE_SCHEDULE_ID.getSubError()),
            new Object[] {schedule.id});
    }
    SchedulerDefinitionEntity saved = repository.saveAndFlush(schedule);
    SchedulerDefinition definition = toDefinition(saved);
    afterCommit(() -> scheduler.schedule(definition));
    return saved;
  }

  @Transactional(readOnly = true)
  public SchedulerDefinitionEntity retrieve(String id) {
    return repository
        .findById(id)
        .filter(schedule -> Objects.equals(schedule.tenant, tenant()))
        .orElseThrow(() -> notFound(id));
  }

  @Transactional(readOnly = true)
  public List<SchedulerDefinitionEntity> findAll() {
    return repository.findByTenant(tenant());
  }

  @Transactional
  public void delete(String id) {
    repository.delete(retrieve(id));
    afterCommit(() -> scheduler.unschedule(id));
  }

  /** Name is the database's own rule; everything else is the scheduler's. */
  private void validate(SchedulerDefinitionEntity schedule) {
    if (schedule == null) invalid(null, "a schedule is required");
    if (schedule.name == null || schedule.name.isBlank() || schedule.name.length() > 255)
      invalid(schedule.id, "name is required, at most 255 characters");
    SchedulerDefinition definition = toDefinition(schedule);
    if (definition.id == null) definition.id = schedule.name;
    scheduler.validate(definition);
    schedule.timezone = definition.timezone;
  }

  private SchedulerDefinition toDefinition(SchedulerDefinitionEntity entity) {
    SchedulerDefinition definition = new SchedulerDefinition();
    definition.id = entity.id;
    definition.name = entity.name;
    definition.cronExpression = entity.cronExpression;
    definition.timezone = entity.timezone;
    definition.eventId = entity.eventId;
    definition.payload = entity.payload;
    definition.headers =
        entity.headers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(entity.headers);
    definition.tenant = entity.tenant;
    definition.enabled = entity.enabled;
    definition.description = entity.description;
    return definition;
  }

  private String tenant() {
    return ContextContainer.getInstance().getTenant();
  }

  private NotFoundException notFound(String id) {
    return new NotFoundException(
        String.valueOf(ErrorCodes.SCHEDULE_NOT_FOUND.getSubError()), new Object[] {id});
  }

  private void invalid(String id, String reason) {
    throw new BadRequestException(
        String.valueOf(ErrorCodes.INVALID_SCHEDULE.getSubError()), new Object[] {id, reason});
  }

  private void afterCommit(Runnable change) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      change.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            change.run();
          }
        });
  }
}
