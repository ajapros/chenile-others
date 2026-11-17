package org.chenile.scheduler;

public abstract class Constants {
    public static final String SCHEDULER_ENTRY_POINT = "scheduler";
    public static final String EXTENSION = "chenile.scheduler";
    /**
     * Keys formerly copied into the Job Data Map. Since the scheduler became cluster-ready, the job data holds only
     * {@link #JOB_NAME}; these remain for compatibility and are no longer written.
     */
    public static final String SERVICE_DEFINITION = "service.definition";
    public static final String OPERATION_DEFINITION = "operation.definition";
    public static final String CHENILE_ENTRY_POINT = "chenile.entry.point";
    public static final String SCHEDULER_INFO = "scheduler.info";
    public static final String TASK_DISPATCHER = "task.dispatcher";
    public static final String EXECUTION_ID = "scheduler.execution.id";
    public static final String BODY = "body";
    /** The only job data: the job's name. Everything else is looked up when the job fires (cluster-safe). */
    public static final String JOB_NAME = "scheduler.job.name";
    /** Scheduler-context key (kept in memory on each node, never persisted) for this node's SchedulerBuilder. */
    public static final String SCHEDULER_BUILDER = "chenile.scheduler.builder";
}
