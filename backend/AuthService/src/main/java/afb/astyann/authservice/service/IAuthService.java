package afb.astyann.authservice.service;

import afb.astyann.authservice.dto.AuthResponseDTO;
import afb.astyann.authservice.dto.RegisterRequestDTO;
import afb.astyann.authservice.dto.RegisterResponseDTO;

import java.util.UUID;

public interface IAuthService {

    /** Register a new user account. Sends a verification email. Returns the created user's id and email. */
    RegisterResponseDTO register(RegisterRequestDTO dto);

    /** Verify the account using the 6-digit code emailed at registration. */
    void verifyAccount(UUID userId, String code);

    /** Resend the verification code (e.g. if expired or not received). */
    void resendVerificationCode(String email);

    /** Authenticate a user and return JWT tokens. */
    AuthResponseDTO login(String email, String password);

    /** Invalidate the current session (client-side token discard). */
    void logout();

    /** Initiate a password-reset flow — sends a reset link by email. */
    void resetPassword(String email);

    /** Complete the password reset — validates the token and sets a new password. */
    void confirmResetPassword(String token, String newPassword);
}
