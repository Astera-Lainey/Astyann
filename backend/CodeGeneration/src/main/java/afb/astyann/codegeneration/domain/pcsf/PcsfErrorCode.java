package afb.astyann.codegeneration.domain.pcsf;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PcsfErrorCode {
    private String id;

    /** Screaming-snake constant, e.g. LOAN_NOT_FOUND */
    @JsonAlias({"name", "errorCode", "error_code", "constant"})
    private String code;

    @JsonAlias({"status", "statusCode", "http_status", "status_code"})
    private int httpStatus;

    /** Message with {param} placeholders, e.g. "Loan {id} not found" */
    @JsonAlias({"message", "template", "msg", "error_message"})
    private String messageTemplate;

    /** Simple class name, e.g. LoanNotFoundException */
    @JsonAlias({"exception", "exceptionType", "class", "exception_class"})
    private String exceptionClass;

    @JsonAlias({"module"})
    private String moduleId;
}
