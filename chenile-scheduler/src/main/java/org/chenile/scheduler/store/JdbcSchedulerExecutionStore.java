package org.chenile.scheduler.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.chenile.scheduler.model.ScheduledExecutionRecord;
import org.chenile.scheduler.model.ScheduledExecutionRequest;
import org.chenile.scheduler.model.SchedulerExecutionStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

public class JdbcSchedulerExecutionStore implements SchedulerExecutionStore {
	private final JdbcTemplate jdbcTemplate;

	public JdbcSchedulerExecutionStore(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public boolean tryStartExecution(ScheduledExecutionRequest request) {
		try {
			jdbcTemplate.update("""
					insert into chenile_scheduler_job_execution
					(execution_id, job_name, scheduled_fire_time, actual_fire_time, started_at, status, attempt, launcher)
					values (?, ?, ?, ?, ?, ?, ?, ?)
					""",
					request.getExecutionId(),
					request.getJobName(),
					Timestamp.from(request.getScheduledFireTime()),
					Timestamp.from(request.getActualFireTime()),
					Timestamp.from(Instant.now()),
					SchedulerExecutionStatus.RUNNING.name(),
					request.getAttempt(),
					request.getLauncher());
			return true;
		} catch (DuplicateKeyException e) {
			return false;
		}
	}

	@Override
	public void markSuccess(String executionId, int attempt, String launcherMetadata) {
		update(executionId, attempt, SchedulerExecutionStatus.SUCCESS, null, launcherMetadata);
	}

	@Override
	public void markFailure(String executionId, int attempt, String errorMessage) {
		update(executionId, attempt, SchedulerExecutionStatus.FAILED, errorMessage, null);
	}

	@Override
	public void markTimedOut(String executionId, int attempt, String errorMessage) {
		update(executionId, attempt, SchedulerExecutionStatus.TIMED_OUT, errorMessage, null);
	}

	@Override
	public Optional<ScheduledExecutionRecord> findByExecutionId(String executionId) {
		return jdbcTemplate.query("""
				select execution_id, job_name, scheduled_fire_time, actual_fire_time, started_at, finished_at,
				       status, attempt, duration_millis, error_message, launcher, launcher_metadata
				  from chenile_scheduler_job_execution
				 where execution_id = ?
				""", rs -> rs.next() ? Optional.of(map(rs)) : Optional.empty(), executionId);
	}

	private void update(String executionId, int attempt, SchedulerExecutionStatus status,
			String errorMessage, String launcherMetadata) {
		ScheduledExecutionRecord record = findByExecutionId(executionId).orElse(null);
		Instant finishedAt = Instant.now();
		Long durationMillis = null;
		if (record != null && record.getStartedAt() != null) {
			durationMillis = Duration.between(record.getStartedAt(), finishedAt).toMillis();
		}
		jdbcTemplate.update("""
				update chenile_scheduler_job_execution
				   set finished_at = ?, status = ?, attempt = ?, duration_millis = ?,
				       error_message = ?, launcher_metadata = coalesce(?, launcher_metadata)
				 where execution_id = ?
				""",
				Timestamp.from(finishedAt),
				status.name(),
				attempt,
				durationMillis,
				errorMessage,
				launcherMetadata,
				executionId);
	}

	private ScheduledExecutionRecord map(ResultSet rs) throws SQLException {
		ScheduledExecutionRecord record = new ScheduledExecutionRecord();
		record.setExecutionId(rs.getString("execution_id"));
		record.setJobName(rs.getString("job_name"));
		record.setScheduledFireTime(toInstant(rs.getTimestamp("scheduled_fire_time")));
		record.setActualFireTime(toInstant(rs.getTimestamp("actual_fire_time")));
		record.setStartedAt(toInstant(rs.getTimestamp("started_at")));
		record.setFinishedAt(toInstant(rs.getTimestamp("finished_at")));
		record.setStatus(SchedulerExecutionStatus.valueOf(rs.getString("status")));
		record.setAttempt(rs.getInt("attempt"));
		record.setDurationMillis((Long)rs.getObject("duration_millis"));
		record.setErrorMessage(rs.getString("error_message"));
		record.setLauncher(rs.getString("launcher"));
		record.setLauncherMetadata(rs.getString("launcher_metadata"));
		return record;
	}

	private Instant toInstant(Timestamp timestamp) {
		return timestamp == null ? null : timestamp.toInstant();
	}
}
