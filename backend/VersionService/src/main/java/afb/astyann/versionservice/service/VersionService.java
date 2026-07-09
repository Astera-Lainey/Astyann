package afb.astyann.versionservice.service;

import afb.astyann.versionservice.domain.*;
import afb.astyann.versionservice.dto.CreateSnapshotDTO;
import afb.astyann.versionservice.dto.SnapshotDTO;
import afb.astyann.versionservice.dto.TimelineDTO;
import afb.astyann.versionservice.exception.InvalidSnapshotRequestException;
import afb.astyann.versionservice.exception.SnapshotNotFoundException;
import afb.astyann.versionservice.exception.TimelineNotFoundException;
import afb.astyann.versionservice.repository.SnapshotRepository;
import afb.astyann.versionservice.repository.TimelineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VersionService {

    private final TimelineRepository timelineRepository;
    private final SnapshotRepository snapshotRepository;

    @Transactional
    public SnapshotDTO createSnapshot(CreateSnapshotDTO dto) {
        Timeline timeline = timelineRepository.findByProjectId(dto.getProjectId())
                .orElseGet(() -> timelineRepository.save(
                        Timeline.builder().projectId(dto.getProjectId()).build()));

        UUID artifactId = resolveArtifactId(dto);

        int nextVersion = snapshotRepository
                .findTopByTimelineIdAndArtifactTypeAndArtifactIdOrderByVersionNumberDesc(
                        timeline.getTimelineId(), dto.getArtifactType(), artifactId)
                .map(s -> s.getVersionNumber() + 1)
                .orElse(1);

        snapshotRepository.findByTimelineIdAndArtifactTypeAndArtifactIdAndActiveTrue(
                        timeline.getTimelineId(), dto.getArtifactType(), artifactId)
                .ifPresent(prev -> {
                    prev.setActive(false);
                    snapshotRepository.save(prev);
                });

        Snapshot snapshot = buildSnapshot(dto, timeline.getTimelineId(), nextVersion, artifactId);
        Snapshot saved = snapshotRepository.save(snapshot);
        log.info("Snapshot created: projectId={} artifactType={} artifactId={} versionNumber={}",
                dto.getProjectId(), dto.getArtifactType(), artifactId, nextVersion);
        return toDto(saved);
    }

    private UUID resolveArtifactId(CreateSnapshotDTO dto) {
        return switch (dto.getArtifactType()) {
            case DIAGRAM -> {
                if (dto.getDiagramId() == null) {
                    throw new InvalidSnapshotRequestException("diagramId is required for artifactType DIAGRAM");
                }
                yield dto.getDiagramId();
            }
            case DOCUMENT -> {
                if (dto.getDocumentId() == null) {
                    throw new InvalidSnapshotRequestException("documentId is required for artifactType DOCUMENT");
                }
                yield dto.getDocumentId();
            }
            case CODE -> {
                if (dto.getCodeId() == null) {
                    throw new InvalidSnapshotRequestException("codeId is required for artifactType CODE");
                }
                yield dto.getCodeId();
            }
            case DEPLOYMENT -> {
                if (dto.getPackageId() == null) {
                    throw new InvalidSnapshotRequestException("packageId is required for artifactType DEPLOYMENT");
                }
                yield dto.getPackageId();
            }
        };
    }

    public TimelineDTO getTimeline(UUID projectId) {
        Timeline timeline = timelineRepository.findByProjectId(projectId)
                .orElseThrow(() -> new TimelineNotFoundException(projectId));
        List<SnapshotDTO> snapshots = snapshotRepository.findByTimelineIdOrderBySnapDateAsc(timeline.getTimelineId())
                .stream().map(this::toDto).toList();
        return TimelineDTO.builder()
                .timelineId(timeline.getTimelineId())
                .projectId(projectId)
                .creationDate(timeline.getCreationDate())
                .snapshots(snapshots)
                .build();
    }

    public List<SnapshotDTO> listSnapshots(UUID projectId) {
        return timelineRepository.findByProjectId(projectId)
                .map(t -> snapshotRepository.findByTimelineIdOrderBySnapDateAsc(t.getTimelineId()))
                .orElse(List.of())
                .stream().map(this::toDto).toList();
    }

    public SnapshotDTO getSnapshot(UUID snapId) {
        return snapshotRepository.findById(snapId)
                .map(this::toDto)
                .orElseThrow(() -> new SnapshotNotFoundException(snapId));
    }

    private Snapshot buildSnapshot(CreateSnapshotDTO dto, UUID timelineId, int versionNumber, UUID artifactId) {
        Snapshot snapshot = switch (dto.getArtifactType()) {
            case DIAGRAM -> {
                if (dto.getDiagramId() == null) {
                    throw new InvalidSnapshotRequestException("diagramId is required for artifactType DIAGRAM");
                }
                yield DiagramSnapshot.builder()
                        .diagramId(dto.getDiagramId())
                        .diagramType(dto.getDiagramType())
                        .build();
            }
            case DOCUMENT -> {
                if (dto.getDocumentId() == null) {
                    throw new InvalidSnapshotRequestException("documentId is required for artifactType DOCUMENT");
                }
                yield DocumentSnapshot.builder()
                        .documentId(dto.getDocumentId())
                        .documentType(dto.getDocumentType())
                        .build();
            }
            case CODE -> {
                if (dto.getCodeId() == null) {
                    throw new InvalidSnapshotRequestException("codeId is required for artifactType CODE");
                }
                yield CodeSnapshot.builder()
                        .codeId(dto.getCodeId())
                        .codeLayer(dto.getCodeLayer())
                        .build();
            }
            case DEPLOYMENT -> {
                if (dto.getPackageId() == null) {
                    throw new InvalidSnapshotRequestException("packageId is required for artifactType DEPLOYMENT");
                }
                yield DeploymentSnapshot.builder()
                        .packageId(dto.getPackageId())
                        .build();
            }
        };

        snapshot.setTimelineId(timelineId);
        snapshot.setVersionName(dto.getVersionName());
        snapshot.setVersionNumber(versionNumber);
        snapshot.setEntrySource(dto.getEntrySource());
        snapshot.setTriggerReason(dto.getTriggerReason());
        snapshot.setArtifactPath(dto.getArtifactPath());
        snapshot.setArtifactType(dto.getArtifactType());
        snapshot.setArtifactId(artifactId);
        snapshot.setActive(true);
        return snapshot;
    }

    private SnapshotDTO toDto(Snapshot s) {
        SnapshotDTO.SnapshotDTOBuilder builder = SnapshotDTO.builder()
                .snapId(s.getSnapId())
                .timelineId(s.getTimelineId())
                .versionName(s.getVersionName())
                .versionNumber(s.getVersionNumber())
                .snapDate(s.getSnapDate())
                .entrySource(s.getEntrySource())
                .triggerReason(s.getTriggerReason())
                .artifactPath(s.getArtifactPath())
                .artifactType(s.getArtifactType())
                .artifactId(s.getArtifactId())
                .active(s.isActive());

        if (s instanceof DiagramSnapshot d) {
            builder.diagramId(d.getDiagramId()).diagramType(d.getDiagramType());
        } else if (s instanceof DocumentSnapshot d) {
            builder.documentId(d.getDocumentId()).documentType(d.getDocumentType());
        } else if (s instanceof CodeSnapshot c) {
            builder.codeId(c.getCodeId()).codeLayer(c.getCodeLayer());
        } else if (s instanceof DeploymentSnapshot d) {
            builder.packageId(d.getPackageId());
        }
        return builder.build();
    }
}
