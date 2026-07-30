package ${project.packageName}.controller;

import ${project.packageName}.service.${module.serviceName};
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web slice test for {@link ${module.controllerName}}: verifies the controller is mapped at the
 * expected path and that its response serialises, with the service layer mocked out.
 *
 * <p>Security filters are disabled ({@code addFilters = false}) so this focuses on request
 * mapping rather than authentication — the JWT filter needs a signed token that a slice test
 * cannot produce meaningfully.
 */
@WebMvcTest(controllers = ${module.controllerName}.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class ${module.controllerName}Test {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ${module.serviceName} service;
<#assign listEndpoints = module.endpoints?filter(e -> e.httpMethod == "GET" && !e.hasPathVariable && e.crud)>
<#if (listEndpoints?size > 0)>
<#assign listEp = listEndpoints[0]>

    @Test
    void listEndpointIsMappedAndReturnsOk() throws Exception {
        when(service.${listEp.methodName}()).thenReturn(java.util.List.of());

        mockMvc.perform(get("${module.requestMapping}"))
                .andExpect(status().isOk());
    }
<#else>

    @Test
    void controllerIsWired() {
        // No collection endpoint on this module — the slice starting at all already proves the
        // controller's dependencies resolve and its mappings are valid.
    }
</#if>
}
