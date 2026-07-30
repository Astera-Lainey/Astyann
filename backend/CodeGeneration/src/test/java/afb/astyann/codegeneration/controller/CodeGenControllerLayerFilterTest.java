package afb.astyann.codegeneration.controller;

import afb.astyann.codegeneration.domain.CodeLayer;
import afb.astyann.codegeneration.dto.ValidationReportDTO;
import afb.astyann.codegeneration.service.CodeGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Guards the {@code ?layer=} filter on the layer-scoped endpoints.
 *
 * <p>Regression: {@code /validate} declared no {@code layer} parameter at all and {@code /generate}
 * read layers only from the request body, so Spring silently discarded {@code ?layer=BACKEND} and
 * every layer was processed. Calling the controller directly (rather than through MockMvc) keeps
 * this a fast unit test while still pinning the argument the service receives.
 */
class CodeGenControllerLayerFilterTest {

    private CodeGenerationService service;
    private CodeGenController controller;
    private final UUID projectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = mock(CodeGenerationService.class);
        controller = new CodeGenController(service);
    }

    @SuppressWarnings("unchecked")
    private List<CodeLayer> captureValidateLayers() {
        ArgumentCaptor<List<CodeLayer>> captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(service).validate(eq(projectId), captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<CodeLayer> captureGenerateLayers() {
        ArgumentCaptor<List<CodeLayer>> captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(service).generate(eq(projectId), captor.capture());
        return captor.getValue();
    }

    @Test
    void validatePassesSingleLayerFromQueryParam() {
        when(service.validate(any(), any())).thenReturn(ValidationReportDTO.builder().build());

        controller.validate(projectId.toString(), List.of(CodeLayer.BACKEND), null);

        assertThat(captureValidateLayers()).containsExactly(CodeLayer.BACKEND);
    }

    @Test
    void validateAcceptsCommaSeparatedLayersParam() {
        when(service.validate(any(), any())).thenReturn(ValidationReportDTO.builder().build());

        controller.validate(projectId.toString(), null,
                List.of(CodeLayer.BACKEND, CodeLayer.FRONTEND));

        assertThat(captureValidateLayers())
                .containsExactly(CodeLayer.BACKEND, CodeLayer.FRONTEND);
    }

    @Test
    void validateWithoutLayerParamMeansAllLayers() {
        when(service.validate(any(), any())).thenReturn(ValidationReportDTO.builder().build());

        controller.validate(projectId.toString(), null, null);

        // null tells the service "every layer" — it must not be an empty list, which would
        // otherwise be indistinguishable from an explicit empty selection.
        assertThat(captureValidateLayers()).isNull();
    }

    @Test
    void generateHonoursQueryParamWhenBodyIsAbsent() {
        when(service.generate(any(), any())).thenReturn(List.of());

        controller.generate(projectId.toString(), List.of(CodeLayer.FRONTEND), null, null);

        assertThat(captureGenerateLayers()).containsExactly(CodeLayer.FRONTEND);
    }

    @Test
    void generateMergesBodyAndQueryParamWithoutDuplicates() {
        when(service.generate(any(), any())).thenReturn(List.of());
        var body = new afb.astyann.codegeneration.dto.GenerateCodeRequest();
        body.setLayers(List.of(CodeLayer.BACKEND));

        controller.generate(projectId.toString(), List.of(CodeLayer.BACKEND, CodeLayer.FRONTEND),
                null, body);

        assertThat(captureGenerateLayers())
                .containsExactly(CodeLayer.BACKEND, CodeLayer.FRONTEND);
    }
}
