package org.chenile.scheduler.jobs;

import org.chenile.core.model.ChenileServiceDefinition;
import org.chenile.core.model.OperationDefinition;
import org.chenile.scheduler.Constants;
import org.chenile.scheduler.launcher.ScheduledTaskDispatcher;
import org.chenile.scheduler.model.SchedulerInfo;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

public class ScheduledJob implements Job {

	
	public ScheduledJob() {
	}
	
	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException {
		try {
			getTaskDispatcher(context).dispatch(getSchedulerInfo(context), getServiceDefinition(context),
					getOperationDefinition(context), context.getScheduledFireTime(), context.getFireTime());
		} catch (Exception e) {
			throw new JobExecutionException(e);
		}
	}

	private ChenileServiceDefinition getServiceDefinition(JobExecutionContext context) {
		return getFromMap(Constants.SERVICE_DEFINITION,context);
	}
	
	private OperationDefinition getOperationDefinition(JobExecutionContext context) {
		return getFromMap(Constants.OPERATION_DEFINITION,context);
	}

	private SchedulerInfo getSchedulerInfo(JobExecutionContext context) {
		return getFromMap(Constants.SCHEDULER_INFO,context);
	}

	private ScheduledTaskDispatcher getTaskDispatcher(JobExecutionContext context) {
		return getFromMap(Constants.TASK_DISPATCHER,context);
	}
	
	@SuppressWarnings({"unchecked" })
	private <T> T getFromMap(String key, JobExecutionContext context) {
		return (T) context.getMergedJobDataMap().get(key);
	}

}
