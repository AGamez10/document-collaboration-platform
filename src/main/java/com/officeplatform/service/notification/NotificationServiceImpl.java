package com.officeplatform.service.notification;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.officeplatform.dto.response.NotificationListResponse;
import com.officeplatform.dto.response.NotificationResponse;
import com.officeplatform.entity.NotificationEntity;
import com.officeplatform.repository.NotificationRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    /** Enough to cover what anyone scrolls in a popover; older ones are noise. */
    private static final int MAX_NOTIFICATIONS = 30;

    private final NotificationRepository notificationRepository;

    public NotificationServiceImpl(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    public NotificationListResponse list(String recipientUserId) {
        String recipient = normalize(recipientUserId);
        if (recipient == null) {
            // A caller with no cédula has no notifications: an empty list is the honest answer,
            // and returning everybody's would be a leak.
            return NotificationListResponse.builder()
                    .notifications(List.of())
                    .unreadCount(0)
                    .build();
        }

        List<NotificationResponse> items = notificationRepository
                .findAllByRecipientUserIdOrderByCreatedAtDesc(recipient, PageRequest.of(0, MAX_NOTIFICATIONS))
                .stream()
                .map(this::toResponse)
                .toList();

        return NotificationListResponse.builder()
                .notifications(items)
                .unreadCount(notificationRepository.countByRecipientUserIdAndReadFalse(recipient))
                .build();
    }

    /**
     * Written in its own transaction: the caller is in the middle of opening a document, and a
     * failure to record a notification must not roll that back. Same reasoning as the activity log.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyResourceOpened(String ownerUserId, String actorUserId, String actorName,
                                     Long resourceId, String resourceType, String resourceName) {
        String owner = normalize(ownerUserId);
        String actor = normalize(actorUserId);

        // Diagnóstico explícito: cuando una notificación no aparece, lo primero que hay que
        // saber es si se descartó y por qué. Sin esto, la ausencia es indistinguible de un fallo.
        if (owner == null) {
            log.debug("Notificación descartada: el archivo {} no tiene autor resoluble (actor={})",
                    resourceId, actorUserId);
            return;
        }
        if (actor == null) {
            log.debug("Notificación descartada: quien abrió el archivo {} no tiene identidad", resourceId);
            return;
        }
        if (owner.equalsIgnoreCase(actor)) {
            // Avisarle a alguien que abrió su propio archivo enterraría lo que sí importa.
            log.debug("Notificación descartada: autor y actor son la misma persona ({}) en el archivo {}",
                    owner, resourceId);
            return;
        }

        String who = (actorName != null && !actorName.isBlank()) ? actorName.trim() : actor;
        String what = (resourceName != null && !resourceName.isBlank()) ? resourceName.trim() : "un documento";

        notificationRepository.save(NotificationEntity.builder()
                .recipientUserId(owner)
                .title("Documento visualizado")
                .message(who + " abrió tu documento '" + what + "'")
                .resourceId(resourceId)
                .resourceType(resourceType)
                .actorUserId(actor)
                .actorName(who)
                .read(false)
                .createdAt(LocalDateTime.now())
                .build());

        log.info("Notificación generada: autor={}, actor={}, archivo={} ({})",
                owner, actor, resourceId, what);
    }

    @Override
    @Transactional
    public boolean markAsRead(Long notificationId, String recipientUserId) {
        String recipient = normalize(recipientUserId);
        if (recipient == null || notificationId == null) {
            return false;
        }
        return notificationRepository.findById(notificationId)
                // Scoped to the owner so one person cannot clear another's notifications.
                .filter(n -> recipient.equalsIgnoreCase(n.getRecipientUserId()))
                .map(n -> {
                    n.setRead(true);
                    notificationRepository.save(n);
                    return true;
                })
                .orElse(false);
    }

    @Override
    @Transactional
    public int markAllAsRead(String recipientUserId) {
        String recipient = normalize(recipientUserId);
        return recipient == null ? 0 : notificationRepository.markAllAsRead(recipient);
    }

    private NotificationResponse toResponse(NotificationEntity entity) {
        return NotificationResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .message(entity.getMessage())
                .resourceId(entity.getResourceId())
                .resourceType(entity.getResourceType())
                .actorUserId(entity.getActorUserId())
                .actorName(entity.getActorName())
                .read(entity.isRead())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}
