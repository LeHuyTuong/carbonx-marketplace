package com.carbonx.identity.application.port.out;

import com.carbonx.identity.domain.user.Role;
import com.carbonx.identity.domain.user.User;

import java.util.List;
import java.util.Optional;

/** Outbound persistence port for the User aggregate (database-per-service, no cross-context joins). */
public interface UserRepositoryPort {
    Optional<User> findById(Long id);

    Optional<User> findByEmail(String email);

    Optional<User> findByOauthSubject(String oauthSubject);

    boolean existsByEmail(String email);

    User save(User user);

    List<User> findAllOrdered();

    List<User> findByRole(Role role);
}
