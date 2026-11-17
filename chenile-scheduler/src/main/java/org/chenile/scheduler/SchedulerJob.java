package org.chenile.scheduler;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;

/**
 * The only Quartz job type. Its job data holds just the definition id, so it is safe for a
 * clustered JDBC job store; it finds this node's {@link ChenileScheduler} in the Quartz scheduler
 * context.
 */
@DisallowConcurrentExecution
public class SchedulerJob implements Job {
  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    try {
      Object scheduler = context.getScheduler().getContext().get(ChenileScheduler.CONTEXT_KEY);
      if (!(scheduler instanceof ChenileScheduler chenileScheduler))
        throw new JobExecutionException("No ChenileScheduler registered on this node");
      chenileScheduler.fire(
          context.getMergedJobDataMap().getString(ChenileScheduler.DEFINITION_ID),
          context.getScheduledFireTime(),
          context.getFireTime());
    } catch (SchedulerException e) {
      throw new JobExecutionException(e);
    }
  }
}
