package org.chenile.scheduler.jobs;

import org.chenile.scheduler.Constants;
import org.chenile.scheduler.init.SchedulerBuilder;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * The Quartz job behind every Chenile scheduled job. Its job data holds only the job's name, so it can be stored in
 * a clustered JDBC job store; it finds this node's {@link SchedulerBuilder} in the scheduler context and lets it
 * resolve and dispatch the job.
 */
public class ScheduledJob implements Job {

	public ScheduledJob() {
	}

	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException {
		try {
			SchedulerBuilder builder =
					(SchedulerBuilder) context.getScheduler().getContext().get(Constants.SCHEDULER_BUILDER);
			if (builder == null)
				throw new IllegalStateException("No SchedulerBuilder registered in this node's scheduler context");
			String jobName = context.getMergedJobDataMap().getString(Constants.JOB_NAME);
			builder.fire(jobName, context.getScheduledFireTime(), context.getFireTime());
		} catch (Exception e) {
			throw new JobExecutionException(e);
		}
	}
}
