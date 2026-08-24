package com.officeplatform.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.officeplatform.entity.ActivityLogEntity;

@Repository
public interface ActivityLogRepository
        extends JpaRepository<ActivityLogEntity, Long>, JpaSpecificationExecutor<ActivityLogEntity> {

    List<ActivityLogEntity> findAllByTimestampAfter(LocalDateTime after);

}
