package afb.astyann.authservice.service.impl;

import afb.astyann.authservice.domain.User;
import afb.astyann.authservice.dto.AuthResponseDTO;
import afb.astyann.authservice.dto.RegisterRequestDTO;
import afb.astyann.authservice.dto.RegisterResponseDTO;
import afb.astyann.authservice.exception.*;
import afb.astyann.authservice.repository.UserRepository;
import afb.astyann.authservice.security.JwtUtil;
import afb.astyann.authservice.service.IAuthService;
import afb.astyann.authservice.util.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AuthServiceImpl implements IAuthService {

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil         jwtUtil;
    private final EmailService    emailService;

    private static final int    CODE_LENGTH             = 6;
    private static final int    CODE_EXPIRY_MINUTES     = 15;
    private static final int    RESET_TOKEN_EXPIRY_MINUTES = 30;
    private static final SecureRandom RANDOM            = new SecureRandom();

    // ── Register ────────────────────────────────────────────────────────────


    public RegisterResponseDTO register(RegisterRequestDTO dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new UserAlreadyExistsException(
                    "An account with this email already exists");
        }

        String verificationCode = generateVerificationCode();

        User user = User.builder()
                .email(dto.getEmail())
                .password(passwordEncoder.encode(dto.getPassword()))
                .isVerified(false)
                .verificationCode(verificationCode)
                .verificationExpiryDate(LocalDateTime.now().plusMinutes(CODE_EXPIRY_MINUTES))
                .build();

        userRepository.save(user);
        log.info("New user registered: {}", dto.getEmail());
        emailService.sendVerificationEmail(dto.getEmail(), verificationCode);

        return RegisterResponseDTO.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .build();
    }

    // ── Verify Account ──────────────────────────────────────────────────────

    @Override
    public void verifyAccount(UUID userId, String code) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (user.isVerified()) {
            return;
        }

        if (user.getVerificationCode() == null || !user.getVerificationCode().equals(code)) {
            throw new InvalidVerificationCodeException("Invalid verification code");
        }

        if (user.getVerificationExpiryDate().isBefore(LocalDateTime.now())) {
            throw new InvalidVerificationCodeException(
                    "Verification code has expired. Please request a new one.");
        }

        user.setVerified(true);
        user.setVerificationCode(null);
        user.setVerificationExpiryDate(null);
        userRepository.save(user);
        log.info("Account verified for user: {}", user.getEmail());
    }

    // ── Resend Verification Code ────────────────────────────────────────────

    @Override
    public void resendVerificationCode(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (user.isVerified()) return;

        String newCode = generateVerificationCode();
        user.setVerificationCode(newCode);
        user.setVerificationExpiryDate(LocalDateTime.now().plusMinutes(CODE_EXPIRY_MINUTES));
        userRepository.save(user);

        emailService.sendVerificationEmail(email, newCode);
        log.info("Verification code resent to {}", email);
    }

    // ── Login ───────────────────────────────────────────────────────────────

    @Override
    public AuthResponseDTO login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException("Invalid credentials"));

        if (!user.isVerified()) {
            throw new AccountNotVerifiedException(
                    "Account not verified. Please check your email.");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new AuthException("Invalid credentials");
        }

        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        log.info("User logged in: {}", email);

        return AuthResponseDTO.builder()
                .accessToken(jwtUtil.generateAccessToken(user))
                .refreshToken(jwtUtil.generateRefreshToken(user))
                .userId(user.getUserId())
                .email(user.getEmail())
                .build();
    }

    // ── Logout ──────────────────────────────────────────────────────────────

    @Override
    public void logout() {
        // JWT is stateless — the client discards the token.
        log.debug("Logout called — client must discard tokens");
    }

    // ── Reset Password ──────────────────────────────────────────────────────

    @Override
    public void resetPassword(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            String resetToken = UUID.randomUUID().toString();
            user.setResetToken(resetToken);
            user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(RESET_TOKEN_EXPIRY_MINUTES));
            userRepository.save(user);
            emailService.sendPasswordResetEmail(email, resetToken);
            log.info("Password reset email sent to {}", email);
        });
    }

    // ── Confirm Reset Password ──────────────────────────────────────────────

    @Override
    public void confirmResetPassword(String token, String newPassword) {
        User user = userRepository.findByResetToken(token)
                .orElseThrow(() -> new InvalidResetTokenException("Invalid or expired reset token"));

        if (user.getResetTokenExpiry() == null || user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new InvalidResetTokenException("Reset token has expired. Please request a new one.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);
        log.info("Password reset completed for user: {}", user.getEmail());
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private String generateVerificationCode() {
        int code = 100_000 + RANDOM.nextInt(900_000);
        return String.valueOf(code);
    }
}
