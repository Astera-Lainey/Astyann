package afb.astyann.diagramgeneratorservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableFeignClients
@EnableAsync
public class DiagramGeneratorServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiagramGeneratorServiceApplication.class, args);
    }

}
