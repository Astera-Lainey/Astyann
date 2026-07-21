package afb.astyann.diagramgeneratorservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SnapshotRetryScheduler {

    private final DiagramGenerationService diagramGenerationService;

    @Scheduled(
            initialDelayString = "${diagram.snapshot.retry.initial-delay-ms:60000}",
            fixedDelayString = "${diagram.snapshot.retry.fixed-delay-ms:60000}")
    public void retry() {
        diagramGenerationService.retryPendingSnapshots();
    }
}