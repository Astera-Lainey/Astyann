package afb.astyann.aiorchestrator.client;

import afb.astyann.aiorchestrator.domain.AITaskType;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "metamodel-service", url = "${services.metamodel.url:http://localhost:8092}")
public interface MetaModelServiceClient {

    @GetMapping("/api/v1/metamodel/constraints")
    String getConstraints(@RequestParam AITaskType taskType);

    @GetMapping("/api/v1/metamodel/template")
    String getPromptTemplate(@RequestParam AITaskType taskType);
}
