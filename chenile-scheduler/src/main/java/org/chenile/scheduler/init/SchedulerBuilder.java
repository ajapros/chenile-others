package org.chenile.scheduler.init;

import org.chenile.core.context.ChenileExchangeBuilder;
import org.chenile.core.model.ChenileConfiguration;
import org.chenile.core.model.ChenileServiceDefinition;
import org.chenile.core.model.OperationDefinition;
import org.chenile.core.model.SubscriberVO;
import org.chenile.scheduler.Constants;
import org.chenile.scheduler.jobs.ScheduledJob;
import org.chenile.scheduler.launcher.ScheduledTaskDispatcher;
import org.chenile.scheduler.model.SchedulerInfo;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;


/**
 * Set up the scheduler for the relevant services if applicable
 * @author Raja Shankar Kolluru
 *
 */
public class SchedulerBuilder implements DisposableBean{

	public static Logger logger = LoggerFactory.getLogger(SchedulerBuilder.class);
	@Autowired
	private ChenileConfiguration chenileConfiguration;
	@Autowired
	private Scheduler quartzScheduler;
	@Autowired
	private ChenileExchangeBuilder chenileExchangeBuilder;
	@Autowired
	private ScheduledTaskDispatcher scheduledTaskDispatcher;
	
	public SchedulerBuilder() {
		super();
	}

	@SuppressWarnings("unchecked")
	@EventListener(ApplicationReadyEvent.class)
	public void build() throws Exception {
		// Each node registers itself in the scheduler context (in memory, never persisted); a job fired on this
		// node finds it there. This keeps the job data map to plain strings, as a clustered JDBC job store needs.
		quartzScheduler.getContext().put(Constants.SCHEDULER_BUILDER, this);
		Map<String, SchedulerInfo> map = (Map<String,SchedulerInfo>)
				chenileConfiguration.getOtherExtensions().get(Constants.EXTENSION);
		if (map == null || map.isEmpty()) return;

		new ArrayList<>(map.values()).forEach(schedulerInfo->{
					try{
						scheduleJob(schedulerInfo);
					} catch (SchedulerException e) {
						logger.error("Cannot schedule job {}. Error = {}", schedulerInfo.jobName,
								e.getMessage());
					}
			});
		quartzScheduler.start();
	}
	
	public void scheduleJob(SchedulerInfo schedulerInfo) throws
	  SchedulerException {
		SubscriberVO subscriberVo = chenileExchangeBuilder.makeSubscriberVO(schedulerInfo.serviceName,
				schedulerInfo.operationName);
		scheduleJob(subscriberVo.serviceDefinition, subscriberVo.operationDefinition,schedulerInfo);
	}
	

	public void scheduleJob(ChenileServiceDefinition serviceDefinition, 
			OperationDefinition operationDefinition,SchedulerInfo schedulerInfo) throws SchedulerException {
		// Strings only: a JDBC job store serializes this map (org.quartz.jobStore.useProperties=true enforces it).
		// The service, operation, headers and payload are looked up from the job name when the job fires.
		// The job fires by name, so the definition must be known on this node, including jobs scheduled in code.
		definitions().put(schedulerInfo.getJobName(), schedulerInfo);
		JobDataMap jdm = new JobDataMap();
		jdm.put(Constants.JOB_NAME, schedulerInfo.getJobName());
		JobDetail job = newJob(ScheduledJob.class)
	             .withIdentity(schedulerInfo.getJobName())
	             .setJobData(jdm)
	             .withDescription(schedulerInfo.getJobDescription())
	             .build();
		CronTrigger trigger = newTrigger()
			    .withIdentity(schedulerInfo.getTriggerName(), schedulerInfo.getTriggerGroup())
			    .withSchedule(cronSchedule(schedulerInfo.getCronSchedule()))
			    .build();
		// replace=true: when several nodes start together, the last one wins instead of failing.
		quartzScheduler.scheduleJob(job, Set.of(trigger), true);
	}

	/**
	 * Runs a scheduled job on whichever node fired its trigger, resolving the job's definition on this node.
	 * Called by {@link ScheduledJob}.
	 */
	public void fire(String jobName, Date scheduledFireTime, Date fireTime) throws Exception {
		SchedulerInfo schedulerInfo = definitions().get(jobName);
		if (schedulerInfo == null) {
			logger.warn("Scheduled job {} is not defined on this node; skipping", jobName);
			return;
		}
		SubscriberVO subscriberVo = chenileExchangeBuilder.makeSubscriberVO(schedulerInfo.serviceName,
				schedulerInfo.operationName);
		scheduledTaskDispatcher.dispatch(schedulerInfo, subscriberVo.serviceDefinition,
				subscriberVo.operationDefinition, scheduledFireTime, fireTime);
	}

	/**
	 * This node's job definitions: the JSON-declared ones registered by ChenileSchedulerInitializer, plus any scheduled
	 * in code through {@link #scheduleJob}. In a cluster, a job scheduled in code is known only on the node that
	 * scheduled it, so it should be scheduled the same way on every node (for example at startup).<br/>
	 * This is typically a test fixture since jobs are seldom scheduled in code. They should be either in configuration
	 * or must use the crontab-service module in chenile=process-management
	 */
	@SuppressWarnings("unchecked")
	private synchronized Map<String, SchedulerInfo> definitions() {
		return (Map<String, SchedulerInfo>) chenileConfiguration.getOtherExtensions()
				.computeIfAbsent(Constants.EXTENSION, key -> new HashMap<String, SchedulerInfo>());
	}

	@Override
	public void destroy() throws Exception {
		if(quartzScheduler.isStarted())
			quartzScheduler.shutdown(true); // wait for jobs to complete		
	}
}
