package com.officeplatform.security.admin;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.officeplatform.entity.AdminUserEntity;
import com.officeplatform.repository.AdminUserRepository;

@Service
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminUserRepository adminUserRepository;

    public AdminUserDetailsService(AdminUserRepository adminUserRepository) {
        this.adminUserRepository = adminUserRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AdminUserEntity adminUser = adminUserRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario administrador no encontrado: " + username));

        return new User(
                adminUser.getUsername(),
                adminUser.getPasswordHash(),
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

}
