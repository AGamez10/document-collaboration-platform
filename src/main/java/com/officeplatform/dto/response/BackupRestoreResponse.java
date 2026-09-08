package com.officeplatform.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Outcome of restoring a backup package.
 *
 * <p>Reports what was created versus updated per table, because a restore over a live database is
 * a synchronisation rather than a wipe-and-load: rows already present are refreshed and missing
 * ones are recreated. {@code warnings} collects the non-fatal problems — typically a binary that
 * could not be streamed back into object storage — so a partially successful restore is visible
 * instead of silently passing as complete.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupRestoreResponse {

    private String fileName;
    private LocalDateTime restoredAt;

    private int apiKeysCreated;
    private int apiKeysUpdated;
    private int knownUsersCreated;
    private int knownUsersUpdated;
    private int foldersCreated;
    private int foldersUpdated;
    private int filesCreated;
    private int filesUpdated;

    private int binariesRestored;
    private int binariesSkipped;

    /** Reglas de acceso reinstaladas: sin ellas una restauracion abre lo que estaba restringido. */
    private int sharePermissionsCreated;
    private int sharePermissionsUpdated;

    private List<String> warnings;
}
