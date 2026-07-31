package ${project.packageName}.controller;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Issues the JWT that {@code JwtFilter} expects, so the generated frontend can sign in.
 *
 * <p><strong>Scaffold authentication.</strong> Credentials come from configuration
 * ({@code app.auth.username} / {@code app.auth.password}) rather than a user table, because the
 * specification does not describe an identity store. This is enough for the generated frontend and
 * backend to work together end to end; replace it with a real user store, password hashing and
 * refresh tokens before going to production.
 */
@RestController
@RequestMapping("${project.versionPrefix}/auth")
public class AuthController {

    @Value("${'$'}{app.auth.username}")
    private String configuredUsername;

    @Value("${'$'}{app.auth.password}")
    private String configuredPassword;

    /** Comma-separated roles granted to the scaffold user. */
    @Value("${'$'}{app.auth.roles:}")
    private String configuredRoles;

    @Value("${'$'}{jwt.secret}")
    private String jwtSecret;

    @Value("${'$'}{jwt.access-token-validity-ms}")
    private long accessTokenValidityMs;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        boolean valid = configuredUsername.equals(request.email())
                && configuredPassword.equals(request.password());
        if (!valid) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<String> roles = Arrays.stream(configuredRoles.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .toList();

        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        String token = Jwts.builder()
                .subject(request.email())
                // JwtFilter reads this claim and prefixes each value with "ROLE_".
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(accessTokenValidityMs)))
                .signWith(key)
                .compact();

        return ResponseEntity.ok(new LoginResponse(token));
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    public record LoginResponse(String token) {}
}
