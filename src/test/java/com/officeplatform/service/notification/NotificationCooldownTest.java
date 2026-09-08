package com.officeplatform.service.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.officeplatform.entity.NotificationEntity;
import com.officeplatform.repository.NotificationRepository;

/**
 * The cooldown that keeps folder browsing from flooding the recipient.
 *
 * <p>Listing a folder happens on every navigation, so the same "opened your folder" message would
 * otherwise be recorded dozens of times in a single browsing session and bury everything else.
 */
@ExtendWith(MockitoExtension.class)
class NotificationCooldownTest {

    private static final String OWNER = "1004356866";
    private static final String ACTOR = "2222222222";

    @Mock private NotificationRepository notificationRepository;
    @InjectMocks private NotificationServiceImpl service;

    @Test
    @DisplayName("the same action on the same resource is recorded once within the window")
    void collapsesRepeatsOfTheSameAction() {
        for (int i = 0; i < 5; i++) {
            service.notifyResourceAction(OWNER, ACTOR, "Beto", 50L, "FOLDER",
                    "Carpeta visualizada", "Beto abrió tu carpeta 'Calidad'");
        }

        verify(notificationRepository, times(1)).save(any(NotificationEntity.class));
    }

    @Test
    @DisplayName("a different resource is a different notification")
    void differentResourceIsNotCollapsed() {
        service.notifyResourceAction(OWNER, ACTOR, "Beto", 50L, "FOLDER",
                "Carpeta visualizada", "Beto abrió tu carpeta 'Calidad'");
        service.notifyResourceAction(OWNER, ACTOR, "Beto", 51L, "FOLDER",
                "Carpeta visualizada", "Beto abrió tu carpeta 'Compras'");

        verify(notificationRepository, times(2)).save(any(NotificationEntity.class));
    }

    @Test
    @DisplayName("a different action on the same resource still gets through")
    void differentActionIsNotCollapsed() {
        // Abrir y editar el mismo documento son hechos distintos: colapsarlos escondería
        // justamente el que más le importa al autor.
        service.notifyResourceAction(OWNER, ACTOR, "Beto", 50L, "FILE",
                "Documento visualizado", "Beto abrió tu documento");
        service.notifyResourceAction(OWNER, ACTOR, "Beto", 50L, "FILE",
                "Documento editado", "Beto editó tu documento");

        verify(notificationRepository, times(2)).save(any(NotificationEntity.class));
    }

    @Test
    @DisplayName("a different actor on the same resource is a separate notification")
    void differentActorIsNotCollapsed() {
        service.notifyResourceAction(OWNER, ACTOR, "Beto", 50L, "FOLDER",
                "Carpeta visualizada", "Beto abrió tu carpeta");
        service.notifyResourceAction(OWNER, "3333333333", "Caro", 50L, "FOLDER",
                "Carpeta visualizada", "Caro abrió tu carpeta");

        verify(notificationRepository, times(2)).save(any(NotificationEntity.class));
    }

}
