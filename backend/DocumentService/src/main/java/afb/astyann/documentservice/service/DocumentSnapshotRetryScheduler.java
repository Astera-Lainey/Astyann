package afb.astyann.documentservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DocumentSnapshotRetryScheduler {

    private final DocumentGenerationService documentGenerationService;

    @Scheduled(
            initialDelayString = "${document.snapshot.retry.initial-delay-ms:60000}",
            fixedDelayString = "${document.snapshot.retry.fixed-delay-ms:60000}")
    public void retry() {
        documentGenerationService.retryPendingSnapshots();
    }
}
