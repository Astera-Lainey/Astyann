package afb.astyann.codegeneration.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "codeExecutor")
    public Executor codeExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("code-gen-");
        executor.initialize();
        return executor;
    }

    /**
     * Bounded pool for the AI logic-injection pass — modules are independent, so they are
     * implemented in parallel (one AI call each). Kept small on purpose so a large PCSF does
     * not fan out dozens of concurrent requests at the AI Orchestrator.
     */
    @Bean(name = "logicExecutor")
    public Executor logicExecutor(
            @org.springframework.beans.factory.annotation.Value("${codegen.ai.logic-injection.concurrency:3}")
            int concurrency) {
        int size = Math.max(1, concurrency);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(size);
        executor.setMaxPoolSize(size);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("logic-");
        executor.initialize();
        return executor;
    }
}
