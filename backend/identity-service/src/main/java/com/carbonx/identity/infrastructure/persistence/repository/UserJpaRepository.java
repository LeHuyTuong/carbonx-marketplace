package com.carbonx.identity.infrastructure.persistence.repository;

import com.carbonx.identity.infrastructure.persistence.entity.UserJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<UserJpaEntity, Long> {
    Optional<UserJpaEntity> findByEmail(String email);

    Optional<UserJpaEntity> findByOauthSub(String oauthSub);

    boolean existsByEmail(String email);

    List<UserJpaEntity> findAllByOrderByIdDesc();

    List<UserJpaEntity> findByRoleOrderByIdDesc(String role);
}
