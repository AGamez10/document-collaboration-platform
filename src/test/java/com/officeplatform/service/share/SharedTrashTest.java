package com.officeplatform.service.share;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.officeplatform.entity.SharePermissionEntity;
import com.officeplatform.entity.SharePermissionEntity.PermissionLevel;
import com.officeplatform.entity.SharePermissionEntity.ResourceType;
import com.officeplatform.entity.SharePermissionEntity.TargetType;
import com.officeplatform.repository.SharePermissionRepository;

/**
 * Each recipient's own trash for the things shared with them.
 *
 * <p>The trash used to be written on {@code FileEntity.deletedAt}, which is a single global flag:
 * a recipient who removed a shared document from their view removed it for everyone, its author
 * included. The mark now lives on the grant, so one person hiding a resource is invisible to the
 * others. These cases pin that isolation, the round trip back out of the trash, and the difference
 * between hiding a resource and giving up access to it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SharedTrashTest {

    @Mock private SharePermissionRepository repository;

    private final List<SharePermissionEntity> rows = new ArrayList<>();
    private ShareServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(repository.findAllByResourceTypeAndResourceIdAndTargetTypeAndTargetUserId(
                any(), anyLong(), any(), anyString()))
                .thenAnswer(i -> rows.stream()
                        .filter(g -> g.getResourceType() == i.getArgument(0))
                        .filter(g -> i.getArgument(1).equals(g.getResourceId()))
                        .filter(g -> g.getTargetType() == i.getArgument(2))
                        .filter(g -> i.getArgument(3).equals(g.getTargetUserId()))
                        .toList());
        lenient().when(repository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        service = new ShareServiceImpl(repository, null, null, null, null, null);
    }

    private SharePermissionEntity grant(Long resourceId, String targetUserId) {
        SharePermissionEntity g = SharePermissionEntity.builder()
                .id((long) (rows.size() + 1))
                .resourceType(ResourceType.FILE)
                .resourceId(resourceId)
                .sourceApiKeyId(3L)
                .targetType(TargetType.USER)
                .targetUserId(targetUserId)
                .permissionLevel(PermissionLevel.EDIT)
                .build();
        rows.add(g);
        return g;
    }

    @Test
    @DisplayName("discarding a shared resource marks only the grant of the person who discarded it")
    void discardingIsolatesOnePerson() {
        SharePermissionEntity ana = grant(31L, "111");
        SharePermissionEntity beto = grant(31L, "222");

        int touched = service.discardForUser(ResourceType.FILE, 31L, "111");

        assertThat(touched).isEqualTo(1);
        assertThat(ana.getDeletedAt()).isNotNull();
        // Beto no se entera: el mismo documento sigue en sus compartidos.
        assertThat(beto.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("restoring puts the resource back without ever touching the file itself")
    void restoringClearsTheMark() {
        SharePermissionEntity ana = grant(31L, "111");
        service.discardForUser(ResourceType.FILE, 31L, "111");

        int touched = service.restoreForUser(ResourceType.FILE, 31L, "111");

        assertThat(touched).isEqualTo(1);
        assertThat(ana.getDeletedAt()).isNull();
        // El nivel de acceso sobrevive el viaje: reaparece como estaba, no degradado.
        assertThat(ana.getPermissionLevel()).isEqualTo(PermissionLevel.EDIT);
    }

    @Test
    @DisplayName("discarding keeps access, so the resource can still be restored from the trash")
    void discardingDoesNotRevokeAccess() {
        SharePermissionEntity ana = grant(31L, "111");

        service.discardForUser(ResourceType.FILE, 31L, "111");

        // La fila sigue existiendo: descartar oculta, purgar es lo que retira el acceso.
        assertThat(rows).contains(ana);
        verify(repository, org.mockito.Mockito.never()).deleteAll(any());
    }

    @Test
    @DisplayName("purging a shared resource gives up the access instead of destroying the file")
    void purgingRemovesOnlyTheGrant() {
        grant(31L, "111");
        grant(31L, "222");

        int removed = service.revokeAccessForUser(ResourceType.FILE, 31L, "111");

        assertThat(removed).isEqualTo(1);
        // Se borra la concesión, nunca el archivo: destruir el documento del autor porque un
        // destinatario vació su papelera sería desproporcionado.
        verify(repository).deleteAll(any());
    }

    @Test
    @DisplayName("acting on a resource nobody shared with this person is a no-op, not a failure")
    void isANoOpWhenThereIsNothingShared() {
        grant(31L, "222");

        assertThat(service.discardForUser(ResourceType.FILE, 31L, "111")).isZero();
        assertThat(service.restoreForUser(ResourceType.FILE, 31L, "111")).isZero();
        assertThat(service.revokeAccessForUser(ResourceType.FILE, 31L, "111")).isZero();
    }

    @Test
    @DisplayName("a blank identity never touches anyone's grants")
    void ignoresABlankIdentity() {
        grant(31L, "111");

        assertThat(service.discardForUser(ResourceType.FILE, 31L, null)).isZero();
        assertThat(service.discardForUser(ResourceType.FILE, 31L, "  ")).isZero();
        assertThat(service.revokeAccessForUser(ResourceType.FILE, null, "111")).isZero();
    }

}
