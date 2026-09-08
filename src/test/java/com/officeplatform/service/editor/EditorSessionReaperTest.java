package com.officeplatform.service.editor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.officeplatform.entity.ActivityAction;
import com.officeplatform.entity.EditorSessionEntity;
import com.officeplatform.onlyoffice.service.OnlyOfficeService;
import com.officeplatform.repository.EditorSessionRepository;
import com.officeplatform.service.activity.ActivityLogRecorder;

/**
 * Reaping of idle editing sessions.
 *
 * <p>The point of the reaper is freeing OnlyOffice connections, of which Community Edition has
 * twenty. Closing the database row alone achieves nothing: the connection lives in Document
 * Server, so the user must be dropped there. These cases pin both halves, and the behaviour when
 * the drop fails — which is precisely when the row most needs closing.
 */
@ExtendWith(MockitoExtension.class)
class EditorSessionReaperTest {

    private static final int INACTIVITY_MINUTES = 10;

    @Mock private EditorSessionRepository editorSessionRepository;
    @Mock private OnlyOfficeService onlyOfficeService;
    @Mock private ActivityLogRecorder activityLogRecorder;

    private EditorSessionReaper reaper() {
        return new EditorSessionReaper(editorSessionRepository, onlyOfficeService,
                activityLogRecorder, INACTIVITY_MINUTES);
    }

    private EditorSessionEntity session(Long id, String documentKey, String userId) {
        EditorSessionEntity s = new EditorSessionEntity();
        s.setId(id);
        s.setFileId(100L);
        s.setFileName("informe.docx");
        s.setDocumentKey(documentKey);
        s.setUserId(userId);
        s.setUserName("Juan Test");
        s.setApiKeyId(7L);
        s.setOpenedAt(LocalDateTime.now().minusHours(1));
        return s;
    }

    @Test
    @DisplayName("an idle session is dropped from Document Server and marked closed")
    void closesIdleSession() {
        EditorSessionEntity stale = session(1L, "doc-key-1", "1004356866");
        when(editorSessionRepository.findStaleSessions(any())).thenReturn(List.of(stale));

        reaper().reapInactiveSessions();

        // The connection is released where it actually lives.
        verify(onlyOfficeService).dropUser("doc-key-1", "1004356866");

        ArgumentCaptor<EditorSessionEntity> saved = ArgumentCaptor.forClass(EditorSessionEntity.class);
        verify(editorSessionRepository).save(saved.capture());
        assertThat(saved.getValue().getClosedAt()).isNotNull();
    }

    @Test
    @DisplayName("the closure is recorded as SESSION_TIMEOUT")
    void recordsTheTimeout() {
        when(editorSessionRepository.findStaleSessions(any()))
                .thenReturn(List.of(session(1L, "doc-key-1", "1004356866")));

        reaper().reapInactiveSessions();

        verify(activityLogRecorder).record(eq(7L), eq("1004356866"), eq("Juan Test"),
                eq(ActivityAction.SESSION_TIMEOUT), eq(100L), eq("informe.docx"), anyString());
    }

    @Test
    @DisplayName("a failed drop still closes the row: the slot is lost either way")
    void closesEvenWhenTheDropFails() {
        EditorSessionEntity stale = session(1L, "doc-key-1", "1004356866");
        when(editorSessionRepository.findStaleSessions(any())).thenReturn(List.of(stale));
        doThrow(new RuntimeException("Document Server no responde"))
                .when(onlyOfficeService).dropUser(anyString(), anyString());

        reaper().reapInactiveSessions();

        // Leaving it open would make the reaper retry the same session on every sweep.
        assertThat(stale.getClosedAt()).isNotNull();
        verify(editorSessionRepository).save(stale);
    }

    @Test
    @DisplayName("one broken session does not stop the sweep")
    void keepsGoingAfterAFailure() {
        EditorSessionEntity first = session(1L, "doc-a", "111");
        EditorSessionEntity second = session(2L, "doc-b", "222");
        when(editorSessionRepository.findStaleSessions(any())).thenReturn(List.of(first, second));
        doThrow(new RuntimeException("timeout")).when(onlyOfficeService).dropUser("doc-a", "111");

        reaper().reapInactiveSessions();

        verify(onlyOfficeService).dropUser("doc-b", "222");
        verify(editorSessionRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("nothing happens when no session is idle")
    void doesNothingWhenAllSessionsAreActive() {
        when(editorSessionRepository.findStaleSessions(any())).thenReturn(List.of());

        reaper().reapInactiveSessions();

        verify(onlyOfficeService, never()).dropUser(anyString(), anyString());
        verify(editorSessionRepository, never()).save(any());
        verify(activityLogRecorder, never()).record(anyLong(), anyString(), anyString(),
                any(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("the cutoff is the configured window back from now")
    void usesTheConfiguredWindow() {
        when(editorSessionRepository.findStaleSessions(any())).thenReturn(List.of());
        LocalDateTime before = LocalDateTime.now().minusMinutes(INACTIVITY_MINUTES);

        reaper().reapInactiveSessions();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(editorSessionRepository).findStaleSessions(cutoff.capture());
        assertThat(cutoff.getValue())
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(LocalDateTime.now().minusMinutes(INACTIVITY_MINUTES));
    }

}
