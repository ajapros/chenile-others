package org.chenile.scheduler.db;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.LinkedHashMap;
import java.util.Map;
import org.chenile.jpautils.entity.BaseJpaEntity;

/** A schedule kept in the database. Same fields as SchedulerDefinition; the tenant is the owner's. */
@Entity
@Table(
    name = "chenile_schedule",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_chenile_schedule_tenant_name",
            columnNames = {"tenant", "name"}),
    indexes = @Index(name = "idx_chenile_schedule_tenant", columnList = "tenant"))
public class SchedulerDefinitionEntity extends BaseJpaEntity {
  @Column(nullable = false)
  public String name;

  @Column(name = "cron_expression", nullable = false)
  public String cronExpression;

  @Column(nullable = false)
  public String timezone = "UTC";

  @Column(name = "event_id", nullable = false)
  public String eventId;

  @Column(columnDefinition = "TEXT")
  public String payload;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "chenile_schedule_header",
      joinColumns = @JoinColumn(name = "schedule_id"))
  @MapKeyColumn(name = "header_name")
  @Column(name = "header_value", length = 1024)
  public Map<String, String> headers = new LinkedHashMap<>();

  @Column(nullable = false)
  public boolean enabled = true;

  public String description;

  @Override
  protected String getPrefix() {
    return "schedule";
  }
}
