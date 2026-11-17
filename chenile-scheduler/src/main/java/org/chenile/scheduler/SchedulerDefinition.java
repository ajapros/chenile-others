package org.chenile.scheduler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One schedule: fire {@link #eventId} on {@link #cronExpression}. The same model comes from JSON
 * files, from code and (with chenile-scheduler-db) from the database.
 */
public class SchedulerDefinition {
  /** Stable identity; also the Quartz job and trigger key. */
  public String id;

  /** For people and the console. */
  public String name;

  /** Quartz syntax, with seconds: {@code 0 0 2 * * ?} is 2 am every day. */
  public String cronExpression;

  /** The zone the cron expression is read in. */
  public String timezone = "UTC";

  /** The Chenile event to fire. Operations subscribe to it. */
  public String eventId;

  /** The event body as JSON text; converted to each subscriber's body type. */
  public String payload;

  /** Extra headers for the event. Every name must start with {@code x-}. */
  public Map<String, String> headers = new LinkedHashMap<>();

  /** The tenant the event fires as. */
  public String tenant;

  /** A disabled definition is kept but not scheduled. */
  public boolean enabled = true;

  public String description;
}
