package com.fcareer.auth.service;

import com.fcareer.auth.entity.AppUserEntity;
import com.fcareer.auth.repository.AccountDeletionRepository;
import com.fcareer.auth.repository.AppUserRepository;
import com.fcareer.auth.security.AuthUser;
import com.fcareer.auth.security.RefreshTokenStore;
import com.fcareer.career.service.CareerService;
import com.fcareer.common.exception.AccountDeletionRejectedException;
import com.fcareer.common.exception.InvalidCredentialsException;
import com.fcareer.common.exception.WrongCurrentPasswordException;
import com.fcareer.ut.kb.LinkTokenStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * PROD-PREP 4 (KVKK): "Hesabımı sil". Tek transaction: mevcut şifre (bcrypt) ve e-posta yazma onayı doğrulanır, son yönetici korunur;
 * kariyerler mevcut kariyer silme yolundan, kalan kişisel kayıtlar ve hesap AccountDeletionRepository ile silinir; katkılar anonimleşir.
 * Oturumlar (refresh + uzantı bağlantı token'ları) iptal edilir. Verilmiş access JWT'ler süresi dolana dek (en çok access-ttl) geçerli kalır;
 * kullanıcı satırı olmadığından /me 404 döner.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountDeletionService {

    private final AppUserRepository appUserRepository;
    private final AccountDeletionRepository deletionRepository;
    private final CareerService careerService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenStore refreshTokenStore;
    private final LinkTokenStore linkTokenStore;

    @Transactional
    public void delete(Long userId, String password, String emailConfirm) {
        AppUserEntity user = appUserRepository.findByIdForUpdate(userId).orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            log.warn("Account deletion rejected, wrong password userId : {}", userId);
            throw new WrongCurrentPasswordException();
        }
        if (!user.getEmail().equals(emailConfirm.trim().toLowerCase(Locale.ROOT))) {
            log.warn("Account deletion rejected, e-mail confirmation mismatch userId : {}", userId);
            throw AccountDeletionRejectedException.emailMismatch();
        }
        if (AuthUser.ROLE_ADMIN.equals(user.getRole()) && deletionRepository.adminCount() <= 1) {
            log.warn("Account deletion rejected, last admin userId : {}", userId);
            throw AccountDeletionRejectedException.lastAdmin();
        }
        List<Long> careers = deletionRepository.careerIds(userId);
        careers.forEach(careerId -> careerService.delete(userId, careerId));
        int anonymized = deletionRepository.anonymizeContributions(userId);
        appUserRepository.flush();
        deletionRepository.deleteUserRows(userId);
        refreshTokenStore.revokeAll(userId);
        linkTokenStore.revokeAll(userId);
        log.info("Account deleted userId : {} careers : {} anonymizedContributions : {}", userId, careers.size(), anonymized);
    }
}
