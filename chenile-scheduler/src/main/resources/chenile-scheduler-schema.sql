create table if not exists chenile_scheduler_job_execution (
	execution_id varchar(128) primary key,
	job_name varchar(200) not null,
	scheduled_fire_time timestamp not null,
	actual_fire_time timestamp,
	started_at timestamp not null,
	finished_at timestamp,
	status varchar(32) not null,
	attempt integer not null,
	duration_millis bigint,
	error_message varchar(4000),
	launcher varchar(32) not null,
	launcher_metadata varchar(4000),
	constraint uk_chenile_scheduler_job_fire unique (job_name, scheduled_fire_time)
);
