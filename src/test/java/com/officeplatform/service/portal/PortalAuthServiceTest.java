package com.officeplatform.service.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.officeplatform.dto.request.PortalChangePasswordRequest;
import com.officeplatform.dto.request.PortalLoginRequest;
import com.officeplatform.dto.response.PortalLoginResponse;
import com.officeplatform.entity.ApiKeyEntity;
import com.officeplatform.entity.PortalUserEntity;
import com.officeplatform.exception.ShareAccessDeniedException;
import com.officeplatform.repository.ApiKeyRepository;
import com.officeplatform.repository.PortalUserRepository;
import com.officeplatform.security.widget.WidgetTokenService;
import com.officeplatform.service.user.KnownUserService;

/**
 * Portal sign-in.
 *
 * <p>The account is created on first sight with a provisional password, which is only acceptable
 * because it grants nothing: no session token is issued while the password has not been changed.
 * These cases pin that guarantee, and the password rules that keep the replacement from being
 * another throwaway.
 */
@ExtendWith(MockitoExtension.class)
class PortalAuthServiceTest {

    private static final String CEDULA = "1004356866";
    private static final Long PORTAL_KEY_ID = 42L;

    @Mock private PortalUserRepository portalUserRepository;
    @Mock private ApiKeyRepository apiKeyRepository;
    @Mock private WidgetTokenService widgetTokenService;
    @Mock private KnownUserService knownUserService;

    private PasswordEncoder encoder;
    private PortalAuthService service;

    @BeforeEach
    void setUp() {
        // A real encoder rather than a mock: the whole point is that hashes are compared,
        // and a stubbed matcher would let a broken comparison pass unnoticed.
        encoder = new BCryptPasswordEncoder();
        service = new PortalAuthService(portalUserRepository, apiKeyRepository,
                widgetTokenService, knownUserService, encoder, "PORTAL WEB");
    }

    private void portalKeyExists() {
        ApiKeyEntity key = new ApiKeyEntity();
        key.setId(PORTAL_KEY_ID);
        key.setName("PORTAL WEB");
        key.setActive(Boolean.TRUE);
        lenient().when(apiKeyRepository.findAll()).thenReturn(List.of(key));
        lenient().when(widgetTokenService.generateToken(anyString(), anyString(), anyLong()))
                .thenReturn("token-de-prueba");
    }

    private PortalUserEntity storedUser(String rawPassword, boolean mustChange) {
        return PortalUserEntity.builder()
                .id(1L)
                .cedula(CEDULA)
                .displayName("Juan Test")
                .passwordHash(encoder.encode(rawPassword))
                .mustChangePassword(mustChange)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("an unknown cédula is registered with the provisional password and must change it")
    void firstSignInRegistersAndForcesChange() {
        when(portalUserRepository.findByCedula(CEDULA)).thenReturn(Optional.empty());
        when(portalUserRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        PortalLoginResponse res = service.login(new PortalLoginRequest(CEDULA, "2026", "Juan Test"));

        assertThat(res.isMustChangePassword()).isTrue();
        // The account exists but grants nothing yet.
        assertThat(res.getToken()).isNull();

        ArgumentCaptor<PortalUserEntity> saved = ArgumentCaptor.forClass(PortalUserEntity.class);
        verify(portalUserRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().isMustChangePassword()).isTrue();
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("2026");
    }

    @Test
    @DisplayName("a first-time visitor who guesses another password is rejected")
    void firstSignInWithWrongPasswordIsRejected() {
        when(portalUserRepository.findByCedula(CEDULA)).thenReturn(Optional.empty());
        when(portalUserRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.login(new PortalLoginRequest(CEDULA, "otra-clave", null)))
                .isInstanceOf(ShareAccessDeniedException.class);
    }

    @Test
    @DisplayName("an account that already changed its password gets a working session")
    void signInReturnsToken() {
        portalKeyExists();
        when(portalUserRepository.findByCedula(CEDULA))
                .thenReturn(Optional.of(storedUser("Segura2026", false)));

        PortalLoginResponse res = service.login(new PortalLoginRequest(CEDULA, "Segura2026", null));

        assertThat(res.isMustChangePassword()).isFalse();
        assertThat(res.getToken()).isEqualTo("token-de-prueba");
        assertThat(res.getCedula()).isEqualTo(CEDULA);
    }

    @Test
    @DisplayName("a wrong password never yields a token")
    void wrongPasswordIsRejected() {
        when(portalUserRepository.findByCedula(CEDULA))
                .thenReturn(Optional.of(storedUser("Segura2026", false)));

        assertThatThrownBy(() -> service.login(new PortalLoginRequest(CEDULA, "incorrecta", null)))
                .isInstanceOf(ShareAccessDeniedException.class);
        verify(widgetTokenService, never()).generateToken(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("a cleared flag does not help while the hash is still the provisional password")
    void provisionalHashStillForcesChange() {
        // Someone flipped must_change_password by hand in the database.
        when(portalUserRepository.findByCedula(CEDULA))
                .thenReturn(Optional.of(storedUser("2026", false)));

        PortalLoginResponse res = service.login(new PortalLoginRequest(CEDULA, "2026", null));

        assertThat(res.isMustChangePassword()).isTrue();
        assertThat(res.getToken()).isNull();
    }

    @Test
    @DisplayName("changing the password clears the flag and returns the session")
    void changePasswordIssuesSession() {
        portalKeyExists();
        PortalUserEntity user = storedUser("2026", true);
        when(portalUserRepository.findByCedula(CEDULA)).thenReturn(Optional.of(user));

        PortalLoginResponse res = service.changePassword(
                new PortalChangePasswordRequest(CEDULA, "2026", "Plastitec2026"));

        assertThat(res.isMustChangePassword()).isFalse();
        assertThat(res.getToken()).isEqualTo("token-de-prueba");
        assertThat(user.isMustChangePassword()).isFalse();
        assertThat(encoder.matches("Plastitec2026", user.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("the new password cannot be the provisional one again")
    void rejectsReusingTheProvisionalPassword() {
        when(portalUserRepository.findByCedula(CEDULA))
                .thenReturn(Optional.of(storedUser("2026", true)));

        assertThatThrownBy(() -> service.changePassword(
                new PortalChangePasswordRequest(CEDULA, "2026", "2026")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the new password must be long enough and mix letters with digits")
    void enforcesPasswordRules() {
        when(portalUserRepository.findByCedula(CEDULA))
                .thenReturn(Optional.of(storedUser("2026", true)));

        assertThatThrownBy(() -> service.changePassword(
                new PortalChangePasswordRequest(CEDULA, "2026", "corta1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("8 caracteres");

        assertThatThrownBy(() -> service.changePassword(
                new PortalChangePasswordRequest(CEDULA, "2026", "solamenteletras")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("letras y números");
    }

    @Test
    @DisplayName("the current password is verified before allowing a change")
    void changeRequiresTheCurrentPassword() {
        when(portalUserRepository.findByCedula(CEDULA))
                .thenReturn(Optional.of(storedUser("Segura2026", false)));

        // Otherwise knowing a cédula would be enough to take over an unused account.
        assertThatThrownBy(() -> service.changePassword(
                new PortalChangePasswordRequest(CEDULA, "adivinada", "Plastitec2026")))
                .isInstanceOf(ShareAccessDeniedException.class);
    }

}
