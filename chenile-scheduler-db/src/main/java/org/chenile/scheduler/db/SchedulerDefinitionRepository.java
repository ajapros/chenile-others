package org.chenile.scheduler.db;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchedulerDefinitionRepository
    extends JpaRepository<SchedulerDefinitionEntity, String> {
  List<SchedulerDefinitionEntity> findByTenant(String tenant);

  Page<SchedulerDefinitionEntity> findByTenant(String tenant, Pageable pageable);
}
