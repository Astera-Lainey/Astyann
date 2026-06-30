package afb.astyann.requirementservice.domain.pcsf;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PcsfApiEndpoint {
    private String id;

    @JsonAlias({"module"})
    private String moduleId;

    @JsonAlias({"method", "verb", "http_method"})
    private String httpMethod;         // GET | POST | PUT | PATCH | DELETE

    @JsonAlias({"url", "route", "uri", "endpoint"})
    private String path;               // e.g. /api/v1/loans/{id}

    @JsonAlias({"name", "operation", "methodName", "operation_id"})
    private String operationId;        // e.g. getLoanById

    @JsonAlias({"description", "desc", "title"})
    private String summary;

    @JsonAlias({"requestBody", "body", "request", "requestBodyRef", "request_body"})
    private String requestBodyEntityId;   // entity id — null for GET/DELETE

    @JsonAlias({"response", "responseBody", "responseRef", "response_body"})
    private String responseEntityId;      // entity id for the response payload

    @JsonAlias({"roles", "allowedRoles", "permissions", "required_roles"})
    @Builder.Default
    private List<String> requiredRoles = new ArrayList<>();

    private boolean paginated;         // true → returns Page<T>
    private boolean requiresAuth;      // false only for public endpoints
}