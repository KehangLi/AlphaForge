package com.likehang.alphaforge.service.quota;

import com.likehang.alphaforge.exception.ResourceNotFoundException;
import com.likehang.alphaforge.exception.ResourceQuotaException;
import com.likehang.alphaforge.model.entity.AppUser;
import com.likehang.alphaforge.model.entity.AppUserType;
import com.likehang.alphaforge.repository.AccountActivityRepository;
import com.likehang.alphaforge.repository.AppUserRepository;
import com.likehang.alphaforge.repository.BrokerageAccountRepository;
import com.likehang.alphaforge.repository.ImportBatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GuestQuotaService {

    public record ImportAllowance(long maxBytes, int maxRows, long remainingActivities) {}

    private final GuestQuotaProperties guest;
    private final UploadProperties uploads;
    private final AppUserRepository users;
    private final BrokerageAccountRepository accounts;
    private final ImportBatchRepository batches;
    private final AccountActivityRepository activities;

    public GuestQuotaService(GuestQuotaProperties guest, UploadProperties uploads, AppUserRepository users,
                             BrokerageAccountRepository accounts, ImportBatchRepository batches,
                             AccountActivityRepository activities) {
        this.guest = guest;
        this.uploads = uploads;
        this.users = users;
        this.accounts = accounts;
        this.batches = batches;
        this.activities = activities;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AppUser lockUser(UUID userId) {
        // Every account creation and import locks the same owner before counting or writing.
        return users.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void checkAccountCreation(AppUser user) {
        if (user.getUserType() == AppUserType.GUEST && accounts.countByUser_Id(user.getId()) >= guest.maxAccounts()) {
            throw new ResourceQuotaException("Guest account limit reached. Delete an account to free space.");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ImportAllowance checkImport(AppUser user) {
        if (user.getUserType() != AppUserType.GUEST) {
            return new ImportAllowance(uploads.maxFileSize().toBytes(), uploads.maxRows(), Long.MAX_VALUE);
        }
        if (batches.countByBrokerageAccount_User_Id(user.getId()) >= guest.maxImportBatches()) {
            throw new ResourceQuotaException("Guest import limit reached. Delete an import to free space.");
        }
        long remaining = guest.maxActivities() - activities.countByBrokerageAccount_User_Id(user.getId());
        if (remaining <= 0) {
            throw new ResourceQuotaException("Guest activity limit reached. Delete an import to free space.");
        }
        return new ImportAllowance(Math.min(guest.maxFileSize().toBytes(), uploads.maxFileSize().toBytes()),
                Math.min(guest.maxRows(), uploads.maxRows()), remaining);
    }

    public void checkActivityCount(ImportAllowance allowance, int newActivities) {
        if (newActivities > allowance.remainingActivities()) {
            throw new ResourceQuotaException("This import exceeds the guest activity limit. Delete an import to free space.");
        }
    }
}
