package com.carbonx.identity.infrastructure.bootstrap;

import com.carbonx.identity.application.port.out.PasswordHasherPort;
import com.carbonx.identity.application.port.out.UserRepositoryPort;
import com.carbonx.identity.domain.user.Role;
import com.carbonx.identity.domain.user.User;
import com.carbonx.identity.domain.user.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapInitializerTest {

    @Mock
    private UserRepositoryPort userRepository;
    @Mock
    private PasswordHasherPort passwordHasher;

    @Test
    @DisplayName("Seeds initial ADMIN user idempotently when empty")
    void seedsAdminWhenAbsent() {
        AdminBootstrapProperties props = new AdminBootstrapProperties(
                true, "Admin@CarbonX.vn", "Admin@123456", "System Admin");
        AdminBootstrapInitializer initializer = new AdminBootstrapInitializer(props, userRepository, passwordHasher);

        given(userRepository.existsByEmail("admin@carbonx.vn")).willReturn(false);
        given(passwordHasher.encode("Admin@123456")).willReturn("hashed-pw");

        initializer.run(new DefaultApplicationArguments());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        then(userRepository).should().save(saved.capture());
        assertThat(saved.getValue().email()).isEqualTo("admin@carbonx.vn");
        assertThat(saved.getValue().role()).isEqualTo(Role.ROLE_ADMIN);
        assertThat(saved.getValue().status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.getValue().passwordHash()).isEqualTo("hashed-pw");
    }

    @Test
    @DisplayName("Skips creation when initial ADMIN account already exists")
    void skipsWhenAlreadyPresent() {
        AdminBootstrapProperties props = new AdminBootstrapProperties(
                true, "admin@carbonx.vn", "Admin@123456", "System Admin");
        AdminBootstrapInitializer initializer = new AdminBootstrapInitializer(props, userRepository, passwordHasher);

        given(userRepository.existsByEmail("admin@carbonx.vn")).willReturn(true);

        initializer.run(new DefaultApplicationArguments());

        then(userRepository).should(never()).save(any());
    }
}
