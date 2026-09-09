package com.officeplatform.service.share;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.officeplatform.dto.request.ShareRequest;
import com.officeplatform.entity.FileEntity;
import com.officeplatform.entity.KnownUserEntity;
import com.officeplatform.entity.SharePermissionEntity;
import com.officeplatform.entity.SharePermissionEntity.PermissionLevel;
import com.officeplatform.entity.SharePermissionEntity.ResourceType;
import com.officeplatform.entity.SharePermissionEntity.TargetType;
import com.officeplatform.exception.ShareAccessDeniedException;
import com.officeplatform.repository.ApiKeyRepository;
import com.officeplatform.repository.FileRepository;
import com.officeplatform.repository.FolderRepository;
import com.officeplatform.repository.KnownUserRepository;
import com.officeplatform.repository.SharePermissionRepository;
import com.officeplatform.security.model.ApiKeyPrincipal;
import com.officeplatform.service.activity.ActivityLogRecorder;

/**
 * Who is allowed to hand a resource's access to someone else.
 *
 * <p>The check used to be "does this caller have EDIT", which looked strict and was not: a file with
 * no restrictions resolves to EDIT for <b>every</b> member of the project, so anyone could re-share
 * a document they merely had access to. Handing out access and editing a document are different
 * powers, and they are now judged separately: the author and project admins always may, and a
 * recipient may only when the grant they received says so.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResharePermissionTest {

    private static final Long API_KEY = 3L;
    private static final Long FILE_ID = 31L;

    @Mock private SharePermissionRepository shareRepository;
    @Mock private FileRepository fileRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private KnownUserRepository knownUserRepository;
    @Mock private ApiKeyRepository apiKeyRepository;
    @Mock private ActivityLogRecorder activityLogRecorder;

    private final List<SharePermissionEntity> grants = new ArrayList<>();
    private ShareServiceImpl service;

    @BeforeEach
    void setUp() {
        FileEntity file = new FileEntity();
        file.setId(FILE_ID);
        file.setApiKeyId(API_KEY);
        file.setOriginalFileName("presupuesto.xlsx");
        file.setCreatedByUserId("111");
        file.setCreatedByName("Ana");

        lenient().when(fileRepository.findByIdAndApiKeyId(anyLong(), anyLong()))
                .thenReturn(Optional.of(file));
        lenient().when(fileRepository.findById(anyLong())).thenReturn(Optional.of(file));
        lenient().when(shareRepository.findAllByResourceTypeAndResourceIdAndSourceApiKeyId(any(), anyLong(), anyLong()))
                .thenAnswer(i -> grants.stream()
                        .filter(g -> i.getArgument(1).equals(g.getResourceId())).toList());
        lenient().when(shareRepository.findAllByResourceTypeAndResourceIdAndTargetTypeAndTargetUserId(
                any(), anyLong(), any(), anyString()))
                .thenAnswer(i -> grants.stream()
                        .filter(g -> i.getArgument(1).equals(g.getResourceId()))
                        .filter(g -> i.getArgument(3).equals(g.getTargetUserId()))
                        .toList());
        lenient().when(shareRepository.save(any(SharePermissionEntity.class)))
                .thenAnswer(i -> { SharePermissionEntity g = i.getArgument(0); g.setId(99L); return g; });
        lenient().when(knownUserRepository.findByApiKeyIdAndUserId(anyLong(), anyString()))
                .thenReturn(Optional.empty());

        service = new ShareServiceImpl(shareRepository, fileRepository, folderRepository,
                knownUserRepository, apiKeyRepository, activityLogRecorder);
    }

    /** Un llamador identificado por cédula, como llega desde el widget. */
    private ApiKeyPrincipal caller(String userId) {
        return new ApiKeyPrincipal(API_KEY, "Proyecto X", userId, "Nombre " + userId);
    }

    private void grantTo(String targetUserId, boolean canShare) {
        grants.add(SharePermissionEntity.builder()
                .id((long) (grants.size() + 1))
                .resourceType(ResourceType.FILE)
                .resourceId(FILE_ID)
                .sourceApiKeyId(API_KEY)
                .targetType(TargetType.USER)
                .targetUserId(targetUserId)
                .permissionLevel(PermissionLevel.EDIT)
                .canShare(canShare)
                .build());
    }

    private ShareRequest requestTo(String targetUserId) {
        ShareRequest r = new ShareRequest();
        r.setResourceType(ResourceType.FILE);
        r.setResourceId(FILE_ID);
        r.setTargetType(TargetType.USER);
        r.setTargetUserId(targetUserId);
        r.setPermissionLevel(PermissionLevel.VIEW);
        return r;
    }

    @Test
    @DisplayName("the author always hands out access to their own document")
    void theAuthorMayShare() {
        assertThat(service.share(requestTo("222"), caller("111"))).isNotNull();
    }

    @Test
    @DisplayName("a recipient granted the power to re-share may pass it on")
    void aDelegatedRecipientMayShare() {
        grantTo("222", true);

        assertThat(service.share(requestTo("333"), caller("222"))).isNotNull();
    }

    @Test
    @DisplayName("a recipient with EDIT but no delegation cannot re-share")
    void aRecipientWithEditAloneMayNotShare() {
        // EDIT dice qué puede hacerle al documento; no dice que pueda repartirlo.
        grantTo("222", false);

        assertThatThrownBy(() -> service.share(requestTo("333"), caller("222")))
                .isInstanceOf(ShareAccessDeniedException.class)
                .hasMessageContaining("re-compartir");
    }

    @Test
    @DisplayName("a grant written before this flag existed does not silently allow re-sharing")
    void aLegacyGrantMayNotShare() {
        // canShare null: las concesiones anteriores a esta columna se leen como "no autorizado",
        // que es la lectura segura para un permiso.
        grants.add(SharePermissionEntity.builder()
                .id(1L).resourceType(ResourceType.FILE).resourceId(FILE_ID)
                .sourceApiKeyId(API_KEY).targetType(TargetType.USER).targetUserId("222")
                .permissionLevel(PermissionLevel.EDIT).build());

        assertThatThrownBy(() -> service.share(requestTo("333"), caller("222")))
                .isInstanceOf(ShareAccessDeniedException.class);
    }

    @Test
    @DisplayName("someone the resource was never shared with cannot re-share it")
    void aStrangerMayNotShare() {
        assertThatThrownBy(() -> service.share(requestTo("333"), caller("999")))
                .isInstanceOf(ShareAccessDeniedException.class);
    }

    @Test
    @DisplayName("a project admin may share anything in their project")
    void aProjectAdminMayShare() {
        KnownUserEntity admin = new KnownUserEntity();
        admin.setApiKeyId(API_KEY);
        admin.setUserId("999");
        admin.setRole("admin");
        lenient().when(knownUserRepository.findByApiKeyIdAndUserId(API_KEY, "999"))
                .thenReturn(Optional.of(admin));

        assertThat(service.share(requestTo("333"), caller("999"))).isNotNull();
    }

    @Test
    @DisplayName("the delegation travels on the grant that gets written")
    void storesTheDelegationOnTheNewGrant() {
        ShareRequest request = requestTo("222");
        request.setCanShare(true);

        assertThat(service.share(request, caller("111")).getCanShare()).isTrue();
    }

    @Test
    @DisplayName("sharing again with someone who had discarded it brings it back to their view")
    void resharingUndoesTheRecipientDiscard() {
        grantTo("222", false);
        grants.get(0).setDeletedAt(java.time.LocalDateTime.now());

        service.share(requestTo("222"), caller("111"));

        // Dejarlo en su papelera haría que el permiso nuevo pareciera no haber llegado nunca.
        assertThat(grants.get(0).getDeletedAt()).isNull();
    }

}
