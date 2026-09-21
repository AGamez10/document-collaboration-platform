package com.officeplatform.service.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.officeplatform.entity.KnownUserEntity;
import com.officeplatform.repository.KnownUserRepository;

/**
 * La promoción a administrador que no llegaba a los proyectos nuevos.
 *
 * <p>El rol se administra por persona: al promover a alguien, el panel escribe "admin" en todas
 * sus inscripciones. Pero esa sincronización solo alcanza a las filas que existen, y una
 * inscripción se crea recién cuando la persona entra por primera vez a ese proyecto. Un
 * administrador promovido hoy entraba mañana a un proyecto nuevo y aparecía como usuario común,
 * sin privilegios sobre los archivos del sistema ni sobre los de los demás — y nadie lo había
 * degradado: la promoción no tenía dónde escribirse.
 *
 * <p>El síntoma se confunde con "registerActivity pisa el rol". No lo pisa; estos casos fijan las
 * dos mitades: la visita repetida respeta el rol, y la inscripción nueva lo hereda.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectAdminPromotionTest {

    private static final Long PROYECTO_CONOCIDO = 1L;
    private static final Long PROYECTO_NUEVO = 2L;
    private static final String CEDULA = "111";

    @Mock private KnownUserRepository knownUserRepository;

    private KnownUserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new KnownUserServiceImpl(knownUserRepository);
        lenient().when(knownUserRepository.saveAndFlush(any(KnownUserEntity.class)))
                .thenAnswer(i -> i.getArgument(0));
        lenient().when(knownUserRepository.save(any(KnownUserEntity.class)))
                .thenAnswer(i -> i.getArgument(0));
    }

    private KnownUserEntity inscripcion(Long apiKeyId, String role) {
        return KnownUserEntity.builder()
                .id(apiKeyId).apiKeyId(apiKeyId).userId(CEDULA).displayName("Ana").role(role).build();
    }

    @Test
    @DisplayName("an admin entering a project for the first time is registered as an admin")
    void aPromotedAdminKeepsTheRoleInANewProject() {
        lenient().when(knownUserRepository.findByApiKeyIdAndUserId(PROYECTO_NUEVO, CEDULA))
                .thenReturn(Optional.empty());
        // Ya es administrador en otro proyecto: esa es la decisión vigente sobre esta persona.
        lenient().when(knownUserRepository.findAllByUserId(CEDULA))
                .thenReturn(List.of(inscripcion(PROYECTO_CONOCIDO, "admin")));

        service.registerActivity(PROYECTO_NUEVO, CEDULA, "Ana");

        ArgumentCaptor<KnownUserEntity> creada = ArgumentCaptor.forClass(KnownUserEntity.class);
        verify(knownUserRepository).saveAndFlush(creada.capture());
        assertThat(creada.getValue().getRole()).isEqualTo("admin");
    }

    @Test
    @DisplayName("someone with no prior role is still an ordinary user")
    void aNewcomerIsAnOrdinaryUser() {
        lenient().when(knownUserRepository.findByApiKeyIdAndUserId(anyLong(), anyString()))
                .thenReturn(Optional.empty());
        lenient().when(knownUserRepository.findAllByUserId(CEDULA)).thenReturn(List.of());

        service.registerActivity(PROYECTO_NUEVO, CEDULA, "Ana");

        ArgumentCaptor<KnownUserEntity> creada = ArgumentCaptor.forClass(KnownUserEntity.class);
        verify(knownUserRepository).saveAndFlush(creada.capture());
        assertThat(creada.getValue().getRole()).isEqualTo("user");
    }

    @Test
    @DisplayName("a demoted person does not get their old role back through a new project")
    void aDemotedPersonStaysDemoted() {
        // Degradar también sincroniza todas las filas: que no quede ninguna en "admin" significa
        // que la decisión vigente es que no lo es.
        lenient().when(knownUserRepository.findByApiKeyIdAndUserId(PROYECTO_NUEVO, CEDULA))
                .thenReturn(Optional.empty());
        lenient().when(knownUserRepository.findAllByUserId(CEDULA))
                .thenReturn(List.of(inscripcion(PROYECTO_CONOCIDO, "user")));

        service.registerActivity(PROYECTO_NUEVO, CEDULA, "Ana");

        ArgumentCaptor<KnownUserEntity> creada = ArgumentCaptor.forClass(KnownUserEntity.class);
        verify(knownUserRepository).saveAndFlush(creada.capture());
        assertThat(creada.getValue().getRole()).isEqualTo("user");
    }

    @Test
    @DisplayName("a revisit never rewrites the role")
    void revisitingDoesNotResetTheRole() {
        // La sospecha razonable era que cada visita degradaba al usuario. No es así, y esta prueba
        // lo deja fijo para que ningún cambio futuro lo convierta en verdad.
        KnownUserEntity existente = inscripcion(PROYECTO_CONOCIDO, "admin");
        lenient().when(knownUserRepository.findByApiKeyIdAndUserId(PROYECTO_CONOCIDO, CEDULA))
                .thenReturn(Optional.of(existente));

        service.registerActivity(PROYECTO_CONOCIDO, CEDULA, "Ana María");

        assertThat(existente.getRole()).isEqualTo("admin");
        assertThat(existente.getDisplayName()).isEqualTo("Ana María");
        assertThat(existente.getLastSeenAt()).isNotNull();
    }
}
