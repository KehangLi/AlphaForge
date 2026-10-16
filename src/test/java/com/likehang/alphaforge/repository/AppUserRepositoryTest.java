package com.likehang.alphaforge.repository;

import com.likehang.alphaforge.model.entity.AppUser;
import com.likehang.alphaforge.model.entity.AppUserType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class AppUserRepositoryTest {

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void savesMultipleGuestsWithoutEmail() {
        AppUser firstGuest = appUserRepository.save(new AppUser(null, "First Guest", AppUserType.GUEST));
        AppUser secondGuest = appUserRepository.save(new AppUser(null, "Second Guest", AppUserType.GUEST));
        appUserRepository.flush();
        entityManager.clear();

        assertThat(firstGuest.getId()).isNotEqualTo(secondGuest.getId());
        for (UUID guestId : new UUID[]{firstGuest.getId(), secondGuest.getId()}) {
            assertThat(appUserRepository.findById(guestId)).hasValueSatisfying(guest -> {
                assertThat(guest.getUserType()).isEqualTo(AppUserType.GUEST);
                assertThat(guest.getEmail()).isNull();
            });
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT user_type FROM app_user WHERE id = ?", String.class, guestId
            )).isEqualTo("GUEST");
        }
    }

    @Test
    void savesExistingConstructorAsRegisteredUser() {
        String email = UUID.randomUUID() + "@example.com";
        AppUser user = appUserRepository.saveAndFlush(new AppUser(email, "Registered User"));
        entityManager.clear();

        assertThat(appUserRepository.findByEmailIgnoreCase(email)).hasValueSatisfying(savedUser -> {
            assertThat(savedUser.getId()).isEqualTo(user.getId());
            assertThat(savedUser.getUserType()).isEqualTo(AppUserType.REGISTERED);
        });
    }

    @Test
    void rejectsDuplicateEmail() {
        String email = UUID.randomUUID() + "@example.com";
        appUserRepository.saveAndFlush(new AppUser(email, "First User"));

        assertThatThrownBy(() -> appUserRepository.saveAndFlush(new AppUser(email, "Second User")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateEmailIgnoringCaseAndOuterWhitespace() {
        String email = UUID.randomUUID() + "@example.com";
        appUserRepository.saveAndFlush(new AppUser(email, "First User"));

        assertThatThrownBy(() -> appUserRepository.saveAndFlush(
                new AppUser(" " + email.toUpperCase(java.util.Locale.ROOT) + " ", "Second User")
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsPasswordCredentialsOnGuestUsers() {
        AppUser guest = new AppUser(null, "Guest", AppUserType.GUEST);
        guest.setPasswordHash("should-not-be-stored");

        assertThatThrownBy(() -> appUserRepository.saveAndFlush(guest))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
