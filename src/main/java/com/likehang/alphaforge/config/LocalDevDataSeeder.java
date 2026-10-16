package com.likehang.alphaforge.config;

import com.likehang.alphaforge.model.entity.AppUser;
import com.likehang.alphaforge.model.entity.AppUserType;
import com.likehang.alphaforge.model.entity.BrokerageAccount;
import com.likehang.alphaforge.repository.AppUserRepository;
import com.likehang.alphaforge.repository.BrokerageAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Component
@Profile("local")
@ConditionalOnProperty(
        prefix = "alphaforge.dev.seed-data",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class LocalDevDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalDevDataSeeder.class);

    private final AppUserRepository appUserRepository;
    private final BrokerageAccountRepository brokerageAccountRepository;
    private final String userEmail;
    private final String userDisplayName;
    private final String userPassword;
    private final PasswordEncoder passwordEncoder;
    private final SeedBrokerageAccount longTermAccount;
    private final SeedBrokerageAccount shortTermAccount;

    public LocalDevDataSeeder(
            AppUserRepository appUserRepository,
            BrokerageAccountRepository brokerageAccountRepository,
            PasswordEncoder passwordEncoder,
            @Value("${alphaforge.dev.user.email}") String userEmail,
            @Value("${alphaforge.dev.user.display-name}") String userDisplayName,
            @Value("${alphaforge.dev.user.password:}") String userPassword,
            @Value("${alphaforge.dev.brokerage-account-long.broker-name}") String longBrokerName,
            @Value("${alphaforge.dev.brokerage-account-long.account-name}") String longAccountName,
            @Value("${alphaforge.dev.brokerage-account-long.base-currency}") String longBaseCurrency,
            @Value("${alphaforge.dev.brokerage-account-short.broker-name}") String shortBrokerName,
            @Value("${alphaforge.dev.brokerage-account-short.account-name}") String shortAccountName,
            @Value("${alphaforge.dev.brokerage-account-short.base-currency}") String shortBaseCurrency
    ) {
        this.appUserRepository = appUserRepository;
        this.brokerageAccountRepository = brokerageAccountRepository;
        this.userEmail = userEmail.trim();
        this.userDisplayName = userDisplayName.trim();
        this.userPassword = userPassword;
        this.passwordEncoder = passwordEncoder;
        this.longTermAccount = new SeedBrokerageAccount(longBrokerName, longAccountName, longBaseCurrency);
        this.shortTermAccount = new SeedBrokerageAccount(shortBrokerName, shortAccountName, shortBaseCurrency);
    }

    @Override
    @Transactional
    public void run(String... args) {
        AppUser appUser = appUserRepository.findByEmailIgnoreCase(userEmail)
                .orElseGet(() -> appUserRepository.save(
                        new AppUser(userEmail, userDisplayName, AppUserType.REGISTERED)
                ));

        if (!userPassword.isBlank() && appUser.getPasswordHash() == null) {
            if (userPassword.length() < 15 || userPassword.length() > 128) {
                throw new IllegalArgumentException("DEV_USER_PASSWORD must contain between 15 and 128 characters");
            }
            appUser.setPasswordHash(passwordEncoder.encode(userPassword));
            appUserRepository.save(appUser);
        }

        seedBrokerageAccount(appUser, longTermAccount);
        seedBrokerageAccount(appUser, shortTermAccount);

        log.info(
                "Local dev seed data ready: user={}, accounts=[{} / {}, {} / {}]",
                userEmail,
                longTermAccount.brokerName(),
                longTermAccount.accountName(),
                shortTermAccount.brokerName(),
                shortTermAccount.accountName()
        );
    }

    private void seedBrokerageAccount(AppUser appUser, SeedBrokerageAccount account) {
        brokerageAccountRepository
                .findByUser_IdAndBrokerNameIgnoreCaseAndAccountNameIgnoreCase(
                        appUser.getId(),
                        account.brokerName(),
                        account.accountName()
                )
                .orElseGet(() -> brokerageAccountRepository.save(
                        new BrokerageAccount(
                                appUser,
                                account.brokerName(),
                                account.accountName(),
                                account.baseCurrency()
                        )
                ));
    }

    private record SeedBrokerageAccount(
            String brokerName,
            String accountName,
            String baseCurrency
    ) {
        private SeedBrokerageAccount {
            brokerName = brokerName.trim();
            accountName = accountName.trim();
            baseCurrency = baseCurrency.trim().toUpperCase(Locale.ROOT);
        }
    }
}
