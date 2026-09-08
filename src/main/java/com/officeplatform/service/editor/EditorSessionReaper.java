package com.officeplatform.service.editor;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.officeplatform.entity.ActivityAction;
import com.officeplatform.entity.EditorSessionEntity;
import com.officeplatform.onlyoffice.service.OnlyOfficeService;
import com.officeplatform.repository.EditorSessionRepository;
import com.officeplatform.service.activity.ActivityLogRecorder;

import lombok.extern.slf4j.Slf4j;

/**
 * Closes editing sessions whose browser stopped reporting activity.
 *
 * <p>OnlyOffice Community Edition allows 20 concurrent connections. A tab left open on a document
 * holds one of them indefinitely, so a handful of forgotten tabs is enough to lock everybody else
 * out. Marking the row closed is not enough — the connection lives in Document Server, so the user
 * has to be dropped there first.
 *
 * <p>The reaper is deliberately conservative: it drops one user at a time and a failure on one
 * session never stops the sweep, because a session that cannot be dropped is exactly the one whose
 * database row most needs closing.
 */
@Component
@Slf4j
public class EditorSessionReaper {

    private final EditorSessionRepository editorSessionRepository;
    private final OnlyOfficeService onlyOfficeService;
    private final ActivityLogRecorder activityLogRecorder;
    private final int inactivityMinutes;

    public EditorSessionReaper(
            EditorSessionRepository editorSessionRepository,
            OnlyOfficeService onlyOfficeService,
            ActivityLogRecorder activityLogRecorder,
            @Value("${office-platform.editor.inactivity-minutes:10}") int inactivityMinutes) {
        this.editorSessionRepository = editorSessionRepository;
        this.onlyOfficeService = onlyOfficeService;
        this.activityLogRecorder = activityLogRecorder;
        this.inactivityMinutes = inactivityMinutes;
    }

    /**
     * Runs every minute. The window is checked against the cutoff rather than counted down, so a
     * restart of the application does not reset anybody's idle time.
     */
    @Scheduled(fixedDelay = 60000)
    public void reapInactiveSessions() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(inactivityMinutes);
        List<EditorSessionEntity> stale = editorSessionRepository.findStaleSessions(cutoff);
        if (stale.isEmpty()) {
            return;
        }

        int closed = 0;
        for (EditorSessionEntity session : stale) {
            if (closeSession(session)) {
                closed++;
            }
        }
        log.info("Reaper de sesiones: {} sesión(es) inactiva(s) cerrada(s) tras {} minutos sin actividad",
                closed, inactivityMinutes);
    }

    /**
     * Drops the user from Document Server and marks the row closed.
     *
     * <p>Its own transaction so one problematic session cannot roll back the whole sweep. The drop
     * is attempted first but its failure does not prevent the close: leaving the row open would
     * make the reaper retry the same session forever while the slot stays occupied either way.
     */
    @Transactional
    public boolean closeSession(EditorSessionEntity session) {
        try {
            onlyOfficeService.dropUser(session.getDocumentKey(), session.getUserId());
        } catch (Exception e) {
            log.warn("No se pudo desconectar al usuario {} del documento {}: {}. "
                            + "La sesión se cierra igual para liberar el cupo.",
                    session.getUserId(), session.getDocumentKey(), e.getMessage());
        }

        try {
            session.setClosedAt(LocalDateTime.now());
            editorSessionRepository.save(session);

            activityLogRecorder.record(
                    session.getApiKeyId(),
                    session.getUserId(),
                    session.getUserName(),
                    ActivityAction.SESSION_TIMEOUT,
                    session.getFileId(),
                    session.getFileName(),
                    "Sesión cerrada automáticamente tras " + inactivityMinutes + " minutos sin actividad");
            return true;
        } catch (Exception e) {
            log.warn("No se pudo cerrar la sesión {}: {}", session.getId(), e.getMessage());
            return false;
        }
    }

}
