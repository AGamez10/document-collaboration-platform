package com.officeplatform.service.share;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.officeplatform.entity.FileEntity;
import com.officeplatform.entity.FolderEntity;
import com.officeplatform.entity.SharePermissionEntity.ResourceType;
import com.officeplatform.repository.ApiKeyRepository;
import com.officeplatform.repository.FileRepository;
import com.officeplatform.repository.FolderRepository;
import com.officeplatform.repository.KnownUserRepository;
import com.officeplatform.repository.SharePermissionRepository;
import com.officeplatform.security.model.ApiKeyPrincipal;
import com.officeplatform.service.activity.ActivityLogRecorder;

/**
 * Ownership judged on the identity carried by the request.
 *
 * <p>A caller authenticated with the classic {@code X-Api-Key} header has no cédula on the
 * principal and sends it as a request parameter. Reading identity only from the principal made
 * those calls look anonymous, so the author of a file was denied its own delete with 403.
 */
@ExtendWith(MockitoExtension.class)
class ShareServiceImplRequestIdentityTest {

    private static final Long API_KEY_ID = 7L;
    private static final Long FILE_ID = 42L;
    private static final Long FOLDER_ID = 84L;
    private static final String CEDULA = "1004356866";

    @Mock private SharePermissionRepository sharePermissionRepository;
    @Mock private FileRepository fileRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private KnownUserRepository knownUserRepository;
    @Mock private ApiKeyRepository apiKeyRepository;
    @Mock private ActivityLogRecorder activityLogRecorder;

    @InjectMocks private ShareServiceImpl shareService;

    /** Principal produced by X-Api-Key authentication: project known, person unknown. */
    private ApiKeyPrincipal apiKeyOnlyPrincipal() {
        return new ApiKeyPrincipal(API_KEY_ID, "PROYECTO");
    }

    private void storeFileCreatedBy(String creatorUserId, String creatorName) {
        FileEntity file = new FileEntity();
        file.setId(FILE_ID);
        file.setApiKeyId(API_KEY_ID);
        file.setCreatedByUserId(creatorUserId);
        file.setCreatedByName(creatorName);
        lenient().when(fileRepository.findByIdAndApiKeyId(FILE_ID, API_KEY_ID))
                .thenReturn(Optional.of(file));
        lenient().when(knownUserRepository.findByApiKeyIdAndUserId(anyLong(), anyString()))
                .thenReturn(Optional.empty());
    }

    private void storeFolderCreatedBy(String creatorUserId, String creatorName) {
        FolderEntity folder = new FolderEntity();
        folder.setId(FOLDER_ID);
        folder.setApiKeyId(API_KEY_ID);
        folder.setCreatedByUserId(creatorUserId);
        folder.setCreatedByName(creatorName);
        lenient().when(folderRepository.findByIdAndApiKeyId(FOLDER_ID, API_KEY_ID))
                .thenReturn(Optional.of(folder));
        lenient().when(knownUserRepository.findByApiKeyIdAndUserId(anyLong(), anyString()))
                .thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("the author is allowed when the cédula travels as a request parameter")
    void authorAllowedThroughRequestParameter() {
        storeFileCreatedBy(CEDULA, "Juan Test");

        assertThat(shareService.isOwnerOrAdmin(
                ResourceType.FILE, FILE_ID, apiKeyOnlyPrincipal(), CEDULA, "Juan Test")).isTrue();
    }

    @Test
    @DisplayName("this is exactly the case that used to fail: no identity anywhere means denial")
    void anonymousApiKeyCallIsDenied() {
        storeFileCreatedBy(CEDULA, "Juan Test");

        // The regression: the principal carries no cédula and none was forwarded.
        assertThat(shareService.isOwnerOrAdmin(
                ResourceType.FILE, FILE_ID, apiKeyOnlyPrincipal(), null, null)).isFalse();
    }

    @Test
    @DisplayName("another person's cédula in the request does not grant ownership")
    void anotherUserIsStillDenied() {
        storeFileCreatedBy(CEDULA, "Juan Test");

        assertThat(shareService.isOwnerOrAdmin(
                ResourceType.FILE, FILE_ID, apiKeyOnlyPrincipal(), "9999999999", "Otro")).isFalse();
    }

    @Test
    @DisplayName("the forwarded cédula is normalised, so spacing or case does not deny the author")
    void forwardedIdentityIsNormalised() {
        storeFileCreatedBy("  " + CEDULA + " ", "Juan Test");

        assertThat(shareService.isOwnerOrAdmin(
                ResourceType.FILE, FILE_ID, apiKeyOnlyPrincipal(), CEDULA, "Juan Test")).isTrue();
    }

    @Test
    @DisplayName("a legacy file without created_by_user_id falls back to the forwarded name")
    void legacyFileFallsBackToForwardedName() {
        storeFileCreatedBy(null, "Juan Test");

        assertThat(shareService.isOwnerOrAdmin(
                ResourceType.FILE, FILE_ID, apiKeyOnlyPrincipal(), CEDULA, "Juan Test")).isTrue();
    }

    @Test
    @DisplayName("a folder is protected from anyone who did not create it")
    void folderProtectedFromThirdParty() {
        storeFolderCreatedBy(CEDULA, "Juan Test");

        assertThat(shareService.isOwnerOrAdmin(
                ResourceType.FOLDER, FOLDER_ID, apiKeyOnlyPrincipal(), "9999999999", "Otro")).isFalse();
    }

    @Test
    @DisplayName("the author of a folder keeps control of it")
    void folderAuthorAllowed() {
        storeFolderCreatedBy(CEDULA, "Juan Test");

        assertThat(shareService.isOwnerOrAdmin(
                ResourceType.FOLDER, FOLDER_ID, apiKeyOnlyPrincipal(), CEDULA, "Juan Test")).isTrue();
    }

}
