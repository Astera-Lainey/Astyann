package afb.astyann.projectservice.pcsf.service;

import afb.astyann.projectservice.pcsf.model.Pcsf;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Stub — will project PCSF fields into generation-service payloads in a later sprint. */
@Service
@Slf4j
public class ProjectionBuilder {

    public void buildProjection(UUID projectId, Pcsf pcsf) {
        log.info("ProjectionBuilder.buildProjection called for project={} — not yet implemented", projectId);
    }
}
