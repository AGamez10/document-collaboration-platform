package com.officeplatform.service.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.officeplatform.dto.response.NotificationListResponse;
import com.officeplatform.entity.NotificationEntity;
import com.officeplatform.repository.NotificationRepository;

/**
 * Notifications about somebody opening a shared document.
 *
 * <p>Two properties matter and both are easy to break: a person is never told about their own
 * activity, and nobody can read or clear somebody else's notifications.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final String OWNER = "1004356866";
    private static final String ACTOR = "2222222222";

    @Mock private NotificationRepository notificationRepository;
    @InjectMocks private NotificationServiceImpl service;

    private NotificationEntity stored(Long id, String recipient, boolean read) {
        return NotificationEntity.builder()
                .id(id)
                .recipientUserId(recipient)
                .title("Documento visualizado")
                .message("Beto abrió tu documento 'informe.docx'")
                .resourceId(50L)
                .resourceType("FILE")
                .actorUserId(ACTOR)
                .actorName("Beto")
                .read(read)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("opening somebody else's document notifies its author")
    void createsNotificationForTheAuthor() {
        service.notifyResourceOpened(OWNER, ACTOR, "Beto Colega", 50L, "FILE", "informe.docx");

        ArgumentCaptor<NotificationEntity> saved = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationRepository).save(saved.capture());

        NotificationEntity n = saved.getValue();
        assertThat(n.getRecipientUserId()).isEqualTo(OWNER);
        assertThat(n.getActorUserId()).isEqualTo(ACTOR);
        assertThat(n.getMessage()).contains("Beto Colega").contains("informe.docx");
        assertThat(n.isRead()).isFalse();
    }

    @Test
    @DisplayName("opening your own document notifies nobody")
    void doesNotNotifyYourself() {
        service.notifyResourceOpened(OWNER, OWNER, "Ana", 50L, "FILE", "informe.docx");

        // Otherwise the useful notifications would be buried under your own activity.
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("the same cédula with different spacing still counts as yourself")
    void treatsPaddedIdentityAsTheSamePerson() {
        service.notifyResourceOpened("  " + OWNER + " ", OWNER, "Ana", 50L, "FILE", "x.docx");

        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("a resource with no known author notifies nobody")
    void skipsWhenThereIsNoOwner() {
        service.notifyResourceOpened(null, ACTOR, "Beto", 50L, "FILE", "informe.docx");

        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("the actor's cédula is used when no display name is available")
    void fallsBackToTheIdentifier() {
        service.notifyResourceOpened(OWNER, ACTOR, null, 50L, "FILE", "informe.docx");

        ArgumentCaptor<NotificationEntity> saved = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getMessage()).contains(ACTOR);
    }

    @Test
    @DisplayName("listing returns the notifications with their unread count")
    void listsWithUnreadCount() {
        when(notificationRepository.findAllByRecipientUserIdOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(OWNER), any(Pageable.class)))
                .thenReturn(List.of(stored(1L, OWNER, false), stored(2L, OWNER, true)));
        when(notificationRepository.countByRecipientUserIdAndReadFalse(OWNER)).thenReturn(1L);

        NotificationListResponse res = service.list(OWNER);

        assertThat(res.getNotifications()).hasSize(2);
        assertThat(res.getUnreadCount()).isEqualTo(1);
        assertThat(res.getNotifications().get(0).getMessage()).contains("informe.docx");
    }

    @Test
    @DisplayName("a caller with no identity gets an empty list, not everybody's")
    void anonymousCallerGetsNothing() {
        NotificationListResponse res = service.list(null);

        assertThat(res.getNotifications()).isEmpty();
        assertThat(res.getUnreadCount()).isZero();
        verify(notificationRepository, never())
                .findAllByRecipientUserIdOrderByCreatedAtDesc(anyString(), any());
    }

    @Test
    @DisplayName("marking as read works on your own notification")
    void marksOwnNotificationAsRead() {
        NotificationEntity n = stored(1L, OWNER, false);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));

        assertThat(service.markAsRead(1L, OWNER)).isTrue();
        assertThat(n.isRead()).isTrue();
        verify(notificationRepository).save(n);
    }

    @Test
    @DisplayName("you cannot mark somebody else's notification as read")
    void cannotMarkAnotherPersonsNotification() {
        NotificationEntity n = stored(1L, OWNER, false);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));

        assertThat(service.markAsRead(1L, ACTOR)).isFalse();
        assertThat(n.isRead()).isFalse();
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("marking all as read only touches your own")
    void marksAllAsReadForTheCallerOnly() {
        when(notificationRepository.markAllAsRead(OWNER)).thenReturn(4);

        assertThat(service.markAllAsRead(OWNER)).isEqualTo(4);
        verify(notificationRepository).markAllAsRead(OWNER);
    }

}
