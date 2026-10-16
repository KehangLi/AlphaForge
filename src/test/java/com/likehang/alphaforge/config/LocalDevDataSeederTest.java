package com.likehang.alphaforge.config;

import com.likehang.alphaforge.model.entity.AppUser;
import com.likehang.alphaforge.repository.AppUserRepository;
import com.likehang.alphaforge.repository.BrokerageAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LocalDevDataSeederTest {

    private final AppUserRepository appUserRepository = mock(AppUserRepository.class);
    private final BrokerageAccountRepository brokerageAccountRepository = mock(BrokerageAccountRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    @Test
    void initializesConfiguredPasswordForExistingSeedUserWithoutCredentials() {
        AppUser user = new AppUser("dev@alphaforge.local", "Local Dev User");
        when(appUserRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("Local test passphrase")).thenReturn("encoded-password");

        seeder("Local test passphrase").run();

        assertThat(user.getPasswordHash()).isEqualTo("encoded-password");
        verify(appUserRepository).save(user);
    }

    @Test
    void preservesExistingPasswordEvenWhenDevPasswordIsConfigured() {
        AppUser user = new AppUser("dev@alphaforge.local", "Local Dev User");
        user.setPasswordHash("existing-password-hash");
        when(appUserRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));

        seeder("A different test passphrase").run();

        assertThat(user.getPasswordHash()).isEqualTo("existing-password-hash");
        verifyNoInteractions(passwordEncoder);
    }

    private LocalDevDataSeeder seeder(String password) {
        return new LocalDevDataSeeder(
                appUserRepository, brokerageAccountRepository, passwordEncoder,
                "dev@alphaforge.local", "Local Dev User", password,
                "Trading 212", "Main account", "EUR",
                "Trading 212", "Long term", "EUR"
        );
    }
}
