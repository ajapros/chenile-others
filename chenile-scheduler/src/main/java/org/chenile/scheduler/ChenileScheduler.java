package org.chenile.scheduler;

import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

import java.io.InputStream;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import org.chenile.base.exception.BadRequestException;
import org.chenile.base.exception.ConfigurationException;
import org.chenile.scheduler.errorcodes.ErrorCodes;
import org.chenile.trigger.TriggerHeaders;
import org.chenile.trigger.TriggerService;
import org.chenile.trigger.TriggerSources;
import org.chenile.trigger.model.TriggerInput;
import org.quartz.CronExpression;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.Resource;
import tools.jackson.databind.ObjectMapper;

/**
 * Chenile's scheduler. It holds this node's {@link SchedulerDefinition}s, schedules them in Quartz
 * and, when one fires, raises its event through {@link TriggerService}.
 *
 * <p>Definitions come from {@code chenile.scheduler.json.package} at startup, and from code (or
 * chenile-scheduler-db) through {@link #schedule}. Quartz is Spring Boot's: in memory by default,
 * clustered through {@code spring.quartz.*}. In a cluster every node must know every definition, so
 * code that schedules must run on every node, for example at startup.
 */
public class ChenileScheduler implements InitializingBean {
  /** Scheduler-context key for this node's instance: kept in memory, never persisted. */
  public static final String CONTEXT_KEY = "chenile.scheduler";

  /** The only job data: the definition id. */
  public static final String DEFINITION_ID = "chenile.scheduler.definition.id";

  public static final String GROUP = "chenile-scheduler";

  private static final Logger logger = LoggerFactory.getLogger(ChenileScheduler.class);

  private final Scheduler quartz;
  private final TriggerService triggers;
  private final Resource[] jsonResources;
  private final ObjectMapper mapper = new ObjectMapper();
  private final Map<String, SchedulerDefinition> definitions = new ConcurrentHashMap<>();

  public ChenileScheduler(Scheduler quartz, TriggerService triggers, Resource[] jsonResources) {
    this.quartz = quartz;
    this.triggers = triggers;
    this.jsonResources = jsonResources == null ? new Resource[0] : jsonResources;
  }

  /** Registers this node with Quartz and schedules the JSON definitions. */
  @Override
  public void afterPropertiesSet() throws Exception {
    quartz.getContext().put(CONTEXT_KEY, this);
    List<SchedulerDefinition> fromJson = new ArrayList<>();
    for (Resource resource : jsonResources) {
      SchedulerDefinition definition = read(resource);
      for (SchedulerDefinition seen : fromJson)
        if (seen.id != null && seen.id.equals(definition.id))
          throw new ConfigurationException(
              String.valueOf(ErrorCodes.DUPLICATE_SCHEDULE_ID.getSubError()),
              new Object[] {definition.id});
      fromJson.add(definition);
    }
    for (SchedulerDefinition definition : fromJson) schedule(definition);
  }

  /**
   * Adds or replaces a definition and (re)schedules it. A disabled definition is kept but removed
   * from Quartz. Replace-if-exists makes this safe when several nodes start together.
   */
  public void schedule(SchedulerDefinition definition) {
    validate(definition);
    definitions.put(definition.id, definition);
    try {
      JobKey key = new JobKey(definition.id, GROUP);
      if (!definition.enabled) {
        quartz.deleteJob(key);
        return;
      }
      JobDetail job =
          newJob(SchedulerJob.class)
              .withIdentity(key)
              .usingJobData(DEFINITION_ID, definition.id)
              .withDescription(definition.description)
              .build();
      CronTrigger trigger =
          newTrigger()
              .withIdentity(definition.id, GROUP)
              .withSchedule(
                  cronSchedule(definition.cronExpression)
                      .inTimeZone(TimeZone.getTimeZone(ZoneId.of(definition.timezone))))
              .build();
      quartz.scheduleJob(job, Set.of(trigger), true);
    } catch (SchedulerException e) {
      throw new ConfigurationException(
          String.valueOf(ErrorCodes.CANNOT_SCHEDULE.getSubError()),
          new Object[] {definition.id, e.getMessage()},
          e);
    }
  }

  /** Forgets the definition and removes it from Quartz. */
  public void unschedule(String id) {
    definitions.remove(id);
    try {
      quartz.deleteJob(new JobKey(id, GROUP));
    } catch (SchedulerException e) {
      throw new ConfigurationException(
          String.valueOf(ErrorCodes.CANNOT_SCHEDULE.getSubError()),
          new Object[] {id, e.getMessage()},
          e);
    }
  }

  public SchedulerDefinition definition(String id) {
    return definitions.get(id);
  }

  public Collection<SchedulerDefinition> definitions() {
    return List.copyOf(definitions.values());
  }

  /** Raises the definition's event. Called by {@link SchedulerJob} on the node Quartz picked. */
  public void fire(String id, Date scheduledFireTime, Date fireTime) {
    SchedulerDefinition definition = definitions.get(id);
    if (definition == null) {
      logger.warn("Schedule {} is not defined on this node; skipping this fire", id);
      return;
    }
    TriggerInput input = new TriggerInput();
    input.eventId = definition.eventId;
    input.source = TriggerSources.CRON;
    input.payload = definition.payload;
    input.idempotencyKey = "schedule:" + id + ":" + scheduledFireTime.toInstant();
    if (definition.headers != null) input.headers.putAll(definition.headers);
    if (definition.tenant != null) input.headers.put(TriggerHeaders.TENANT_ID, definition.tenant);
    input.headers.put(TriggerHeaders.SCHEDULE_ID, id);
    input.headers.put(TriggerHeaders.SCHEDULED_FIRE_TIME, scheduledFireTime.toInstant().toString());
    input.headers.put(TriggerHeaders.FIRE_TIME, fireTime.toInstant().toString());
    input.triggerContext.put("scheduleId", id);
    triggers.trigger(input);
  }

  private SchedulerDefinition read(Resource resource) {
    try (InputStream in = resource.getInputStream()) {
      return mapper.readValue(in, SchedulerDefinition.class);
    } catch (Exception e) {
      throw new ConfigurationException(
          String.valueOf(ErrorCodes.INVALID_SCHEDULE.getSubError()),
          new Object[] {resource.getFilename(), e.getMessage()},
          e);
    }
  }

  private void validate(SchedulerDefinition definition) {
    if (definition == null) invalid(null, "a definition is required");
    if (definition.id == null || definition.id.isBlank()) invalid(null, "id is required");
    if (definition.eventId == null || definition.eventId.isBlank())
      invalid(definition.id, "eventId is required");
    if (definition.cronExpression == null
        || !CronExpression.isValidExpression(definition.cronExpression))
      invalid(definition.id, "invalid cron expression " + definition.cronExpression);
    if (definition.timezone == null || definition.timezone.isBlank()) definition.timezone = "UTC";
    try {
      ZoneId.of(definition.timezone);
    } catch (DateTimeException e) {
      invalid(definition.id, "invalid timezone " + definition.timezone);
    }
    if (definition.headers != null)
      for (String header : definition.headers.keySet())
        if (header == null || !header.startsWith("x-"))
          invalid(definition.id, "header " + header + " must start with x-");
  }

  private void invalid(String id, String reason) {
    throw new BadRequestException(
        String.valueOf(ErrorCodes.INVALID_SCHEDULE.getSubError()), new Object[] {id, reason});
  }
}
