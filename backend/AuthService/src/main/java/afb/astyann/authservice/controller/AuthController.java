package afb.astyann.authservice.controller;

import afb.astyann.authservice.dto.*;
import afb.astyann.authservice.service.IAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final IAuthService authService;

    /**
     * POST /api/v1/auth/register
     * FR-01: Register a new user account.
     */
    @PostMapping("/register")
    public ResponseEntity<RegisterResponseDTO> register(@Valid @RequestBody RegisterRequestDTO dto) {
        RegisterResponseDTO response = authService.register(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/v1/auth/verify
     * FR-01: Verify email using the code sent during registration.
     */
    @PostMapping("/verify")
    public ResponseEntity<Void> verifyAccount(@Valid @RequestBody VerifyAccountDTO dto) {
        authService.verifyAccount(parseUserId(dto.getUserId()), dto.getCode());
        return ResponseEntity.ok().build();
    }

    private UUID parseUserId(String userId) {
        String hex = userId.startsWith("0x") || userId.startsWith("0X")
                ? userId.substring(2)
                : userId.replace("-", "");
        long mostSigBits = Long.parseUnsignedLong(hex.substring(0, 16), 16);
        long leastSigBits = Long.parseUnsignedLong(hex.substring(16), 16);
        return new UUID(mostSigBits, leastSigBits);
    }

    /**
     * POST /api/v1/auth/verify/resend
     * FR-01 / FR-02: Resend a new verification code.
     */
    @PostMapping("/verify/resend")
    public ResponseEntity<Void> resendVerification(@RequestParam String email) {
        authService.resendVerificationCode(email);
        return ResponseEntity.ok().build();
    }

    /**
     * POST /api/v1/auth/login
     * FR-02: Authenticate user and return JWT tokens.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody LoginRequestDTO dto) {
        AuthResponseDTO response = authService.login(dto.getEmail(), dto.getPassword());
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/auth/logout
     * FR-04: Logout (client discards JWT token).
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authHeader) {
        authService.logout();
        return ResponseEntity.ok().build();
    }

    /**
     * POST /api/v1/auth/reset-password
     * FR-03: Initiate password reset — sends a link to the user's email.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordDTO dto) {
        authService.resetPassword(dto.getEmail());
        return ResponseEntity.ok().build();
    }

    /**
     * POST /api/v1/auth/reset-password/confirm
     * FR-03: Complete password reset with the token from the email.
     */
    @PostMapping("/reset-password/confirm")
    public ResponseEntity<Void> confirmResetPassword(@Valid @RequestBody ResetPasswordConfirmDTO dto) {
        authService.confirmResetPassword(dto.getToken(), dto.getNewPassword());
        return ResponseEntity.ok().build();
    }
}
