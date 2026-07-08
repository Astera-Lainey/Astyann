package afb.astyann.diagramgeneratorservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient krokiRestClient(RestClient.Builder builder,
                                       @Value("${services.kroki.url:https://kroki.io}") String krokiUrl) {
        return builder.baseUrl(krokiUrl).build();
    }
}
