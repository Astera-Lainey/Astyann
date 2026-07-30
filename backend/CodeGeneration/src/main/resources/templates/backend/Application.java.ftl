package ${project.packageName};

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// NOTE: @EnableJpaAuditing lives in config/JpaConfig rather than here on purpose. On the main
// class it is processed by every test slice (@WebMvcTest included), where no JPA context exists,
// and the slice fails to start. In a separate @Configuration it is picked up at runtime by
// component scanning but ignored by web slice tests.
@SpringBootApplication
public class ${appClassName} {

    public static void main(String[] args) {
        SpringApplication.run(${appClassName}.class, args);
    }
}
