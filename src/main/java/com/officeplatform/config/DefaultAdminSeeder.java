package com.officeplatform.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.officeplatform.entity.AdminUserEntity;
import com.officeplatform.repository.AdminUserRepository;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DefaultAdminSeeder implements ApplicationRunner {

    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_PASSWORD = "admin123";

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    public DefaultAdminSeeder(AdminUserRepository adminUserRepository, PasswordEncoder passwordEncoder) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminUserRepository.count() > 0) {
            return;
        }

        AdminUserEntity defaultAdmin = AdminUserEntity.builder()
                .username(DEFAULT_USERNAME)
                .passwordHash(passwordEncoder.encode(DEFAULT_PASSWORD))
                .build();

        adminUserRepository.save(defaultAdmin);

        log.warn("Se creó el usuario administrador por defecto '{}' con la contraseña por defecto. "
                + "Cámbiala antes de pasar a producción.", DEFAULT_USERNAME);
    }

}
