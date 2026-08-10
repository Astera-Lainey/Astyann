package afb.astyann.documentservice.service;

import afb.astyann.documentservice.service.ApiContractDeriver.DerivedEndpoint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The overlay decides what the contract document says exists. These pin the split that makes it
 * useful: the operation list is derived and non-negotiable, while the prose the model wrote about
 * an operation survives as long as it is describing an operation that will exist.
 */
class ApiContractOverlayTest {

    private final ApiContractOverlay overlay = new ApiContractOverlay();
    private final ObjectMapper mapper = new ObjectMapper();

    private static DerivedEndpoint derived(String apiCode, String method, String path, String summary) {
        return new DerivedEndpoint(apiCode, method, path, "op", summary, "Product Management",
                List.of("ADMINISTRATOR"), true, false, false);
    }

    private JsonNode authored(String json) throws Exception {
        return mapper.readTree(json);
    }

    @Test
    void replacesTheModelsOperationListWithTheDerivedOne() throws Exception {
        // The model invented a DELETE and missed the PATCH — the exact drift observed.
        JsonNode data = authored("""
                {"endpoint":[
                   {"apiCode":"API-99","method":"DELETE","path":"/api/v1/products/{id}","description":"Delete"},
                   {"apiCode":"API-98","method":"GET","path":"/api/v1/products","description":"Model's list text"}
                ]}""");

        overlay.apply(data, List.of(
                derived("API-01", "GET", "/api/v1/products", "Paginated list"),
                derived("API-02", "PATCH", "/api/v1/products/{productId}/archive", "Archive")));

        assertThat(data.get("endpoint")).hasSize(2);
        assertThat(data.get("endpoint").get(0).get("method").asText()).isEqualTo("GET");
        assertThat(data.get("endpoint").get(1).get("method").asText()).isEqualTo("PATCH");
        assertThat(data.toString()).doesNotContain("DELETE");
    }

    @Test
    void keepsTheModelsDescriptionWhenItDescribesAnOperationThatWillExist() throws Exception {
        JsonNode data = authored("""
                {"endpoint":[
                   {"method":"GET","path":"/api/v1/products","description":"Retrieve every product, paginated"}
                ]}""");

        overlay.apply(data, List.of(derived("API-01", "GET", "/api/v1/products", "PCSF summary")));

        assertThat(data.get("endpoint").get(0).get("description").asText())
                .isEqualTo("Retrieve every product, paginated");
    }

    @Test
    void fallsBackToThePcsfSummaryWhenTheModelWroteNothingForThatOperation() throws Exception {
        JsonNode data = authored("{\"endpoint\":[]}");

        overlay.apply(data, List.of(derived("API-01", "PATCH", "/api/v1/products/{productId}/archive",
                "Archive a product that cannot be hard-deleted")));

        assertThat(data.get("endpoint").get(0).get("description").asText())
                .isEqualTo("Archive a product that cannot be hard-deleted");
    }

    @Test
    void carriesAuthoredDetailAndTraceProseOntoTheDerivedRow() throws Exception {
        JsonNode data = authored("""
                {"endpointDetail":[
                   {"method":"GET","path":"/api/v1/products","responseSchema":"Page<ProductResponseDto>",
                    "statusCodes":"200, 401","frCovered":"FR-03"}],
                 "trace":[
                   {"method":"GET","path":"/api/v1/products","frCovered":"FR-03","ucCovered":"UC-01","usCovered":"US-07"}]}""");

        overlay.apply(data, List.of(derived("API-01", "GET", "/api/v1/products", "List")));

        JsonNode detail = data.get("endpointDetail").get(0);
        assertThat(detail.get("responseSchema").asText()).isEqualTo("Page<ProductResponseDto>");
        assertThat(detail.get("statusCodes").asText()).isEqualTo("200, 401");
        JsonNode trace = data.get("trace").get(0);
        assertThat(trace.get("ucCovered").asText()).isEqualTo("UC-01");
        assertThat(trace.get("usCovered").asText()).isEqualTo("US-07");
    }

    @Test
    void derivesSecurityAndIdempotencyRatherThanTrustingTheModel() throws Exception {
        JsonNode data = authored("{\"endpointDetail\":[]}");

        overlay.apply(data, List.of(
                derived("API-01", "GET", "/api/v1/products", "List"),
                new DerivedEndpoint("API-02", "POST", "/api/v1/products", "op", "Create",
                        "Product Management", List.of(), false, false, true)));

        assertThat(data.get("endpointDetail").get(0).get("security").asText())
                .isEqualTo("Roles: ADMINISTRATOR");
        assertThat(data.get("endpointDetail").get(0).get("idempotent").asText()).isEqualTo("Yes");
        assertThat(data.get("endpointDetail").get(1).get("security").asText()).isEqualTo("Public");
        assertThat(data.get("endpointDetail").get(1).get("idempotent").asText()).isEqualTo("No");
    }

    @Test
    void rebuildsGroupHeadingsAndTheEndpointCount() throws Exception {
        JsonNode data = authored("{\"endpointGroup\":[{\"name\":\"Invented Group\"}]}");

        overlay.apply(data, List.of(
                derived("API-01", "GET", "/api/v1/products", "List"),
                new DerivedEndpoint("API-02", "GET", "/api/v1/users", "op", "List users",
                        "User Management", List.of(), true, false, false)));

        assertThat(data.get("endpointGroup")).hasSize(2);
        assertThat(data.get("endpointGroup").get(0).get("name").asText()).isEqualTo("Product Management");
        assertThat(data.get("endpointGroup").get(1).get("name").asText()).isEqualTo("User Management");
        assertThat(data.get("coverageStats").get("totalEndpoints").asText()).isEqualTo("2");
    }

    @Test
    void leavesTheDocumentUntouchedWhenNothingCouldBeDerived() throws Exception {
        // A PCSF that could not be read must not blank out the contract the model wrote.
        JsonNode data = authored("""
                {"endpoint":[{"method":"GET","path":"/api/v1/products","description":"Model text"}]}""");

        overlay.apply(data, List.of());

        assertThat(data.get("endpoint")).hasSize(1);
        assertThat(data.get("endpoint").get(0).get("description").asText()).isEqualTo("Model text");
    }

    @Test
    void reportsWhichAuthoredOperationsWillNotExist() throws Exception {
        JsonNode data = authored("""
                {"endpoint":[
                   {"method":"DELETE","path":"/api/v1/products/{id}","description":"Delete"},
                   {"method":"GET","path":"/api/v1/products","description":"List"}]}""");

        List<String> unmatched = overlay.unmatchedAuthoredKeys(data,
                List.of(derived("API-01", "GET", "/api/v1/products", "List")));

        assertThat(unmatched).containsExactly("DELETE /api/v1/products/{id}");
    }
}
