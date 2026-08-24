package com.officeplatform.service.editor;

import com.officeplatform.dto.response.EditorConfigResponse;
import com.officeplatform.onlyoffice.dto.OnlyOfficeCallbackRequest;
import com.officeplatform.security.model.ApiKeyPrincipal;

public interface EditorService {

    EditorConfigResponse getEditorConfig(Long fileId, ApiKeyPrincipal principal, String userId, String userName);

    void processCallback(OnlyOfficeCallbackRequest callback);

    /** Explicit close notification from the widget, independent of OnlyOffice's own callback. */
    void closeSession(Long fileId, String documentKey, Long apiKeyId);

}
