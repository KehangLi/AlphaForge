package com.likehang.alphaforge.config;

import com.likehang.alphaforge.model.entity.AppUser;
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
    private final String brokerName;
    private final String accountName;
    private final String baseCurrency;

    public LocalDevDataSeeder(
            AppUserRepository appUserRepository,
            BrokerageAccountRepository brokerageAccountRepository,
            @Value("${alphaforge.dev.user.email}") String userEmail,
            @Value("${alphaforge.dev.user.display-name}") String userDisplayName,
            @Value("${alphaforge.dev.brokerage-account.broker-name}") String brokerName,
            @Value("${alphaforge.dev.brokerage-account.account-name}") String accountName,
            @Value("${alphaforge.dev.brokerage-account.base-currency}") String baseCurrency
    ) {
        this.appUserRepository = appUserRepository;
        this.brokerageAccountRepository = brokerageAccountRepository;
        this.userEmail = userEmail.trim();
        this.userDisplayName = userDisplayName.trim();
        this.brokerName = brokerName.trim();
        this.accountName = accountName.trim();
        this.baseCurrency = baseCurrency.trim().toUpperCase(Locale.ROOT);
    }

    @Override
    @Transactional
    public void run(String... args) {
        AppUser appUser = appUserRepository.findByEmailIgnoreCase(userEmail)
                .orElseGet(() -> appUserRepository.save(new AppUser(userEmail, userDisplayName)));

        brokerageAccountRepository
                .findByUser_IdAndBrokerNameIgnoreCaseAndAccountNameIgnoreCase(
                        appUser.getId(),
                        brokerName,
                        accountName
                )
                .orElseGet(() -> brokerageAccountRepository.save(
                        new BrokerageAccount(appUser, brokerName, accountName, baseCurrency)
                ));

        log.info(
                "Local dev seed data ready: user={}, broker={}, account={}",
                userEmail,
                brokerName,
                accountName
        );
    }
}
