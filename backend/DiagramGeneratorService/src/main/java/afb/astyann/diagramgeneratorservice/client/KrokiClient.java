package afb.astyann.diagramgeneratorservice.client;

import afb.astyann.diagramgeneratorservice.exception.DownstreamServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@RequiredArgsConstructor
@Slf4j
public class KrokiClient {

    private final RestClient krokiRestClient;

    public byte[] render(String plantUmlSource, String format) {
        String outputFormat = "PNG".equalsIgnoreCase(format) ? "png" : "svg";
        try {
            return krokiRestClient.post()
                    .uri("/plantuml/{fmt}", outputFormat)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(plantUmlSource)
                    .retrieve()
                    .body(byte[].class);
        } catch (RestClientResponseException ex) {
            // Kroki's response body for 4xx is usually the PlantUML syntax error itself —
            // surface it so failures are diagnosable instead of a bare "rendering failed".
            String body = ex.getResponseBodyAsString();
            log.warn("Kroki rejected diagram (status={}): {}\nSource:\n{}", ex.getStatusCode(), body, plantUmlSource);
            throw new DownstreamServiceException(
                    "Kroki rendering failed (" + ex.getStatusCode() + "): " + body, ex);
        } catch (RestClientException ex) {
            log.warn("Kroki call failed: {}", ex.getMessage());
            throw new DownstreamServiceException("Kroki rendering failed: " + ex.getMessage(), ex);
        }
    }
}
