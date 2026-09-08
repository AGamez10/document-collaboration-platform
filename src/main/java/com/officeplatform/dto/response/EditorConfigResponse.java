package com.officeplatform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EditorConfigResponse {

    private Object document;

    private String documentServer;

    private Object user;

    private Object permissions;

    private String token;

    private Object editorConfig;

    /** How many editor sessions are currently open for this file (including this one). */
    private long activeSessionsCount;

    /**
     * Id de la sesión recién abierta. El widget lo necesita para enviar heartbeats:
     * sin él no puede decir cuál de las sesiones del usuario sigue viva.
     */
    private Long sessionId;

}
