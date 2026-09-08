package com.officeplatform.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.officeplatform.entity.PortalUserEntity;

@Repository
public interface PortalUserRepository extends JpaRepository<PortalUserEntity, Long> {

    Optional<PortalUserEntity> findByCedula(String cedula);
}
