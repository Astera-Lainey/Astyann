package afb.astyann.authservice.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@Getter
@ResponseStatus(HttpStatus.FORBIDDEN)
public class AccountNotVerifiedException extends RuntimeException {
    private final String userId;

    public AccountNotVerifiedException(String message, String userId) {
        super(message);
        this.userId = userId;
    }
}
