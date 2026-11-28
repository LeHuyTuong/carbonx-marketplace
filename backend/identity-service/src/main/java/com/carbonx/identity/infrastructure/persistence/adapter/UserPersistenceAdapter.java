package com.carbonx.identity.infrastructure.persistence.adapter;

import com.carbonx.identity.application.port.out.UserRepositoryPort;
import com.carbonx.identity.domain.user.Role;
import com.carbonx.identity.domain.user.User;
import com.carbonx.identity.domain.user.UserStatus;
import com.carbonx.identity.infrastructure.persistence.entity.UserJpaEntity;
import com.carbonx.identity.infrastructure.persistence.repository.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Adapts the User aggregate to the {@code users} table. */
@Component
@RequiredArgsConstructor
public class UserPersistenceAdapter implements UserRepositoryPort {

    private final UserJpaRepository repository;

    @Override
    public Optional<User> findById(Long id) {
        return repository.findById(id).map(UserPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return repository.findByEmail(email).map(UserPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<User> findByOauthSubject(String oauthSubject) {
        return repository.findByOauthSub(oauthSubject).map(UserPersistenceAdapter::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return repository.existsByEmail(email);
    }

    @Override
    public User save(User user) {
        UserJpaEntity persisted = repository.save(toEntity(user));
        return toDomain(persisted);
    }

    @Override
    public List<User> findAllOrdered() {
        return repository.findAllByOrderByIdDesc().stream().map(UserPersistenceAdapter::toDomain).toList();
    }

    @Override
    public List<User> findByRole(Role role) {
        return repository.findByRoleOrderByIdDesc(role.name()).stream()
                .map(UserPersistenceAdapter::toDomain)
                .toList();
    }

    private static User toDomain(UserJpaEntity entity) {
        return User.rehydrate(entity.getId(), entity.getEmail(), entity.getPasswordHash(),
                entity.getFullName(), Role.from(entity.getRole()), entity.getOauthSub(),
                UserStatus.valueOf(entity.getStatus()), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private static UserJpaEntity toEntity(User user) {
        return UserJpaEntity.builder()
                .id(user.id())
                .email(user.email())
                .passwordHash(user.passwordHash())
                .fullName(user.fullName())
                .role(user.role().name())
                .oauthSub(user.oauthSubject())
                .status(user.status().name())
                .build();
    }
}
