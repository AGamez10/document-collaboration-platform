package com.officeplatform.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.officeplatform.entity.NotificationEntity;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {

    List<NotificationEntity> findAllByRecipientUserIdOrderByCreatedAtDesc(
            String recipientUserId, Pageable pageable);

    long countByRecipientUserIdAndReadFalse(String recipientUserId);

    /**
     * Marks every pending notification of one person as read in a single statement.
     *
     * <p>Loading them to flip a boolean one by one would be a round trip per row for something the
     * database resolves in one update.
     */
    @Modifying
    @Query("UPDATE NotificationEntity n SET n.read = true "
            + "WHERE n.recipientUserId = :userId AND n.read = false")
    int markAllAsRead(@Param("userId") String userId);
}
