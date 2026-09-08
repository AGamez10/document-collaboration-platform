package com.officeplatform.service.editor;

import com.officeplatform.dto.response.EditorConfigResponse;
import com.officeplatform.onlyoffice.dto.OnlyOfficeCallbackRequest;
import com.officeplatform.security.model.ApiKeyPrincipal;

public interface EditorService {

    EditorConfigResponse getEditorConfig(Long fileId, ApiKeyPrincipal principal, String userId, String userName);

    void processCallback(OnlyOfficeCallbackRequest callback);

    /** Explicit close notification from the widget, independent of OnlyOffice's own callback. */
    void closeSession(Long fileId, String documentKey, Long apiKeyId);


    /**
     * Records that the browser holding this session is still active.
     *
     * @return {@code true} if an open session was refreshed; {@code false} if it no longer exists
     *         or was already closed, which tells the widget to stop sending heartbeats
     */
    boolean heartbeat(Long sessionId, com.officeplatform.security.model.ApiKeyPrincipal principal);

}
