package com.officeplatform.service.file;

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

import com.officeplatform.entity.FileEntity;
import com.officeplatform.entity.SharePermissionEntity;
import com.officeplatform.entity.SharePermissionEntity.PermissionLevel;
import com.officeplatform.entity.SharePermissionEntity.ResourceType;
import com.officeplatform.entity.SharePermissionEntity.TargetType;
import com.officeplatform.exception.FileNotFoundException;
import com.officeplatform.repository.ApiKeyRepository;
import com.officeplatform.repository.FileRepository;
import com.officeplatform.repository.FolderRepository;
import com.officeplatform.repository.SharePermissionRepository;
import com.officeplatform.service.activity.ActivityLogRecorder;
import com.officeplatform.service.storage.StorageService;
import com.officeplatform.service.version.FileVersionService;

/**
 * Reaching a file that somebody else shared with you.
 *
 * <p>The lookup behind delete, restore and purge resolved only two paths: a file in the caller's own
 * project, and a private file the caller owns elsewhere. A file shared across projects matched
 * neither, so removing it from "Compartidos conmigo" answered 404 before the controller ever got to
 * unlink the grant — the resource existed, the person had access to it, and the platform said it did
 * not exist. Resolving is not authorising: this only decides whether to answer 404, while who may
 * destroy the file is still judged separately by the ownership check.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SharedFileLookupTest {

    private static final Long MY_PROJECT = 3L;
    private static final Long OTHER_PROJECT = 99L;
    private static final Long FILE_ID = 31L;
    private static final String ME = "111";

    @Mock private FileRepository fileRepository;
    @Mock private ApiKeyRepository apiKeyRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private SharePermissionRepository shareRepository;
    @Mock private StorageService storageService;
    @Mock private FileVersionService fileVersionService;
    @Mock private com.officeplatform.service.search.FileIndexingService fileIndexingService;
    @Mock private ActivityLogRecorder activityLogRecorder;

    private final List<SharePermissionEntity> grants = new ArrayList<>();
    private FileServiceImpl service;
    private FileEntity foreignFile;

    @BeforeEach
    void setUp() {
        foreignFile = new FileEntity();
        foreignFile.setId(FILE_ID);
        foreignFile.setApiKeyId(OTHER_PROJECT);
        foreignFile.setOriginalFileName("prueba.docx");
        foreignFile.setCreatedByUserId("222");

        lenient().when(fileRepository.findByIdAndApiKeyId(anyLong(), anyLong())).thenReturn(Optional.empty());
        lenient().when(fileRepository.findById(FILE_ID)).thenReturn(Optional.of(foreignFile));
        lenient().when(shareRepository.findAllByResourceTypeAndResourceIdAndTargetTypeAndTargetUserId(
                any(), anyLong(), any(), anyString()))
                .thenAnswer(i -> grants.stream()
                        .filter(g -> i.getArgument(1).equals(g.getResourceId()))
                        .filter(g -> i.getArgument(3).equals(g.getTargetUserId()))
                        .toList());

        service = new FileServiceImpl(fileRepository, apiKeyRepository, folderRepository,
                shareRepository, storageService, fileVersionService, fileIndexingService, activityLogRecorder,
                "office-platform", "application/pdf");
    }

    private void shareWithMe() {
        grants.add(SharePermissionEntity.builder()
                .id(1L).resourceType(ResourceType.FILE).resourceId(FILE_ID)
                .sourceApiKeyId(OTHER_PROJECT).targetType(TargetType.USER).targetUserId(ME)
                .permissionLevel(PermissionLevel.EDIT).build());
    }

    @Test
    @DisplayName("a file shared from another project resolves instead of answering 404")
    void resolvesAFileSharedFromAnotherProject() {
        shareWithMe();

        // Este era el 404 que impedía quitar algo de "Compartidos conmigo": el archivo existía y
        // la persona tenía acceso, pero la consulta no miraba las concesiones.
        assertThat(service.getFileIncludingTrashed(FILE_ID, MY_PROJECT, ME)).isSameAs(foreignFile);
    }

    @Test
    @DisplayName("a file in the trash stays reachable, which is what lets the trash be emptied")
    void resolvesEvenWhenTrashed() {
        shareWithMe();
        foreignFile.setDeletedAt(java.time.LocalDateTime.now());

        assertThat(service.getFileIncludingTrashed(FILE_ID, MY_PROJECT, ME)).isSameAs(foreignFile);
    }

    @Test
    @DisplayName("someone with no grant over the file still gets a 404")
    void stillHidesAFileNobodyShared() {
        // Resolver no es autorizar, pero tampoco puede volverse un catálogo de todo lo ajeno.
        assertThatThrownBy(() -> service.getFileIncludingTrashed(FILE_ID, MY_PROJECT, ME))
                .isInstanceOf(FileNotFoundException.class);
    }

    @Test
    @DisplayName("a grant aimed at somebody else does not open the file to me")
    void ignoresAGrantForAnotherPerson() {
        grants.add(SharePermissionEntity.builder()
                .id(1L).resourceType(ResourceType.FILE).resourceId(FILE_ID)
                .sourceApiKeyId(OTHER_PROJECT).targetType(TargetType.USER).targetUserId("333")
                .permissionLevel(PermissionLevel.EDIT).build());

        assertThatThrownBy(() -> service.getFileIncludingTrashed(FILE_ID, MY_PROJECT, ME))
                .isInstanceOf(FileNotFoundException.class);
    }

    @Test
    @DisplayName("a caller with no identity cannot reach anything outside their project")
    void requiresAnIdentity() {
        shareWithMe();

        assertThatThrownBy(() -> service.getFileIncludingTrashed(FILE_ID, MY_PROJECT, null))
                .isInstanceOf(FileNotFoundException.class);
    }

    @Test
    @DisplayName("a file in the caller's own project still resolves without consulting grants")
    void keepsResolvingOwnProjectFiles() {
        FileEntity mine = new FileEntity();
        mine.setId(FILE_ID);
        mine.setApiKeyId(MY_PROJECT);
        lenient().when(fileRepository.findByIdAndApiKeyId(FILE_ID, MY_PROJECT)).thenReturn(Optional.of(mine));

        assertThat(service.getFileIncludingTrashed(FILE_ID, MY_PROJECT, ME)).isSameAs(mine);
    }

}
