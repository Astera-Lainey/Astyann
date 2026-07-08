package afb.astyann.diagramgeneratorservice.client;

import afb.astyann.diagramgeneratorservice.exception.DownstreamServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
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
        } catch (RestClientException ex) {
            throw new DownstreamServiceException("Kroki rendering failed.", ex);
        }
    }
}
