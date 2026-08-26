package com.officeplatform.service.share;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import com.officeplatform.entity.KnownUserEntity;
import com.officeplatform.entity.SharePermissionEntity.ResourceType;
import com.officeplatform.repository.ApiKeyRepository;
import com.officeplatform.repository.FileRepository;
import com.officeplatform.repository.FolderRepository;
import com.officeplatform.repository.KnownUserRepository;
import com.officeplatform.repository.SharePermissionRepository;
import com.officeplatform.security.model.ApiKeyPrincipal;
import com.officeplatform.service.activity.ActivityLogRecorder;

/**
 * Ownership resolution for destructive operations (delete, restore, purge).
 *
 * <p>Covers the cases that used to produce false 403s: legacy rows without
 * {@code created_by_user_id}, identifiers that differ only by surrounding whitespace or case,
 * and callers that authenticated with a name but no identifier.
 */
@ExtendWith(MockitoExtension.class)
class ShareServiceImplOwnershipTest {

    private static final Long API_KEY_ID = 7L;
    private static final Long FILE_ID = 42L;

    @Mock private SharePermissionRepository sharePermissionRepository;
    @Mock private FileRepository fileRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private KnownUserRepository knownUserRepository;
    @Mock private ApiKeyRepository apiKeyRepository;
    @Mock private ActivityLogRecorder activityLogRecorder;

    @InjectMocks private ShareServiceImpl shareService;

    /** Stores a file with the given creator metadata and returns true if the caller owns it. */
    private boolean ownedBy(String storedUserId, String storedName, ApiKeyPrincipal caller) {
        FileEntity file = new FileEntity();
        file.setId(FILE_ID);
        file.setApiKeyId(API_KEY_ID);
        file.setCreatedByUserId(storedUserId);
        file.setCreatedByName(storedName);

        lenient().when(fileRepository.findByIdAndApiKeyId(FILE_ID, API_KEY_ID))
                .thenReturn(Optional.of(file));
        lenient().when(knownUserRepository.findByApiKeyIdAndUserId(anyLong(), anyString()))
                .thenReturn(Optional.empty());

        return shareService.isOwnerOrAdmin(ResourceType.FILE, FILE_ID, caller);
    }

    private ApiKeyPrincipal caller(String userId, String userName) {
        return new ApiKeyPrincipal(API_KEY_ID, "PROJECT", userId, userName);
    }

    @Test
    @DisplayName("the author is recognised by their identifier")
    void recognisesAuthorByIdentifier() {
        assertThat(ownedBy("1004356866", "Ana", caller("1004356866", "Ana"))).isTrue();
    }

    @Test
    @DisplayName("surrounding whitespace on the stored identifier does not deny the author")
    void toleratesWhitespaceInStoredIdentifier() {
        assertThat(ownedBy("  1004356866 ", "Ana", caller("1004356866", "Ana"))).isTrue();
    }

    @Test
    @DisplayName("identifiers differing only in case still match")
    void matchesIdentifierIgnoringCase() {
        assertThat(ownedBy("key_ABC", "Ana", caller("key_abc", "Ana"))).isTrue();
    }

    @Test
    @DisplayName("legacy row without created_by_user_id falls back to the display name")
    void fallsBackToNameForLegacyRows() {
        assertThat(ownedBy(null, "Ana Duenia", caller("1004356866", "Ana Duenia"))).isTrue();
    }

    @Test
    @DisplayName("legacy fallback tolerates whitespace and case in the display name")
    void legacyFallbackNormalisesTheName() {
        assertThat(ownedBy(null, " ana duenia ", caller("1004356866", "Ana Duenia"))).isTrue();
    }

    @Test
    @DisplayName("caller authenticated with a name but no identifier is still recognised")
    void recognisesCallerWithoutIdentifier() {
        assertThat(ownedBy(null, "Ana Duenia", caller(null, "Ana Duenia"))).isTrue();
    }

    @Test
    @DisplayName("a blank stored identifier is treated as absent, not as a match")
    void blankStoredIdentifierIsNotAWildcard() {
        assertThat(ownedBy("   ", "Ana Duenia", caller("1004356866", "Beto Intruso"))).isFalse();
    }

    @Test
    @DisplayName("somebody else is denied")
    void deniesAnotherUser() {
        assertThat(ownedBy("1004356866", "Ana", caller("2222222222", "Beto"))).isFalse();
    }

    @Test
    @DisplayName("a stored identifier that does not match is decisive: a matching name cannot override it")
    void identifierMismatchIsNotRescuedByName() {
        // Both users display as "Ana", but the stored identifier belongs to somebody else.
        assertThat(ownedBy("1004356866", "Ana", caller("2222222222", "Ana"))).isFalse();
    }

    @Test
    @DisplayName("a project admin owns every resource")
    void projectAdminAlwaysAllowed() {
        KnownUserEntity admin = new KnownUserEntity();
        admin.setApiKeyId(API_KEY_ID);
        admin.setUserId("9999999999");
        admin.setRole("admin");

        lenient().when(knownUserRepository.findByApiKeyIdAndUserId(API_KEY_ID, "9999999999"))
                .thenReturn(Optional.of(admin));

        assertThat(shareService.isOwnerOrAdmin(
                ResourceType.FILE, FILE_ID, caller("9999999999", "Admin"))).isTrue();
    }

    @Test
    @DisplayName("a file that does not exist is not owned by anyone")
    void missingFileIsNotOwned() {
        lenient().when(fileRepository.findByIdAndApiKeyId(anyLong(), any()))
                .thenReturn(Optional.empty());
        lenient().when(knownUserRepository.findByApiKeyIdAndUserId(anyLong(), anyString()))
                .thenReturn(Optional.empty());

        assertThat(shareService.isOwnerOrAdmin(
                ResourceType.FILE, FILE_ID, caller("1004356866", "Ana"))).isFalse();
    }

}
