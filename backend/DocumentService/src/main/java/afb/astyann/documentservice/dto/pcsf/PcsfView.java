package afb.astyann.documentservice.dto.pcsf;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * A read-only slice of the PCSF — only the parts the API contract document is derived from.
 *
 * <p>Deliberately not a copy of RequirementService's full model. This service needs the declared
 * API surface and enough module/entity context to describe it; mirroring twenty-odd DTOs to get
 * there would create a second thing to keep in sync for no benefit. Every type ignores unknown
 * properties, so the PCSF can grow without breaking document generation.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PcsfView {

    private Project project;
    private ApiConfig apiConfig;
    @JsonIgnoreProperties(ignoreUnknown = true) private List<Module> modules = new ArrayList<>();
    private List<Entity> entities = new ArrayList<>();
    private List<Endpoint> endpoints = new ArrayList<>();

    /** The PCSF wraps most authored values in a provenance envelope; only the value matters here. */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FieldValue<T> {
        private T value;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Project {
        private FieldValue<String> name;
        private FieldValue<String> organisationName;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ApiConfig {
        private String versionPrefix = "/api/v1";
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Module {
        private String id;
        private FieldValue<String> name;
        private FieldValue<List<String>> crudOperations;
        private List<UseCase> useCases = new ArrayList<>();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UseCase {
        private String id;
        private FieldValue<String> name;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Entity {
        private String id;
        private FieldValue<String> name;
        private String primaryModuleId;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Endpoint {
        private String id;
        @JsonAlias({"module"})
        private String moduleId;
        @JsonAlias({"method", "verb", "http_method"})
        private String httpMethod;
        @JsonAlias({"url", "route", "uri", "endpoint"})
        private String path;
        @JsonAlias({"name", "operation", "methodName", "operation_id"})
        private String operationId;
        @JsonAlias({"description", "desc", "title"})
        private String summary;
        private String requestBodyEntityId;
        private String responseEntityId;
        private List<String> requiredRoles = new ArrayList<>();
        private boolean paginated;
        private boolean requiresAuth;
    }
}
