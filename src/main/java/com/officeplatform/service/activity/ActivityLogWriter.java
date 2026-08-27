package com.officeplatform.service.activity;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.officeplatform.entity.ActivityLogEntity;
import com.officeplatform.repository.ActivityLogRepository;

/**
 * Persists an activity entry in its own transaction.
 *
 * <p>Exists as a separate bean on purpose. The audit trail is secondary information and must never
 * take a business operation down with it, but catching the exception at the caller is not enough:
 * a failed INSERT inside the caller's transaction marks it rollback-only, and the commit then fails
 * with {@code UnexpectedRollbackException} no matter how carefully the exception was swallowed.
 * That is exactly how a stale check constraint on {@code activity_log.action} turned every
 * {@code POST /api/share} into a 500 while the permission itself had been written correctly.
 *
 * <p>REQUIRES_NEW suspends the caller's transaction so a failure here rolls back only this insert.
 * The annotation lives on a distinct bean because a self-invocation inside
 * {@link ActivityLogRecorder} would bypass the proxy and silently do nothing.
 */
@Component
public class ActivityLogWriter {

    private final ActivityLogRepository activityLogRepository;

    public ActivityLogWriter(ActivityLogRepository activityLogRepository) {
        this.activityLogRepository = activityLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(ActivityLogEntity entity) {
        activityLogRepository.saveAndFlush(entity);
    }

}
