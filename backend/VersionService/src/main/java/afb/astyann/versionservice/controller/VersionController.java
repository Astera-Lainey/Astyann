package afb.astyann.versionservice.controller;

import afb.astyann.versionservice.dto.ApiResponse;
import afb.astyann.versionservice.dto.CreateSnapshotDTO;
import afb.astyann.versionservice.dto.SnapshotDTO;
import afb.astyann.versionservice.dto.TimelineDTO;
import afb.astyann.versionservice.service.VersionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/versions")
@RequiredArgsConstructor
public class VersionController {

    private final VersionService versionService;

    @PostMapping("/{projectId}/snapshots")
    public ResponseEntity<ApiResponse<SnapshotDTO>> createSnapshot(
            @PathVariable String projectId,
            @Valid @RequestBody CreateSnapshotDTO body) {
        UUID pId = parseId(projectId);
        body.setProjectId(pId); // path is authoritative
        SnapshotDTO result = versionService.createSnapshot(body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<SnapshotDTO>builder()
                        .status(201).message("Snapshot created.").data(result).build());
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ApiResponse<TimelineDTO>> getTimeline(@PathVariable String projectId) {
        TimelineDTO result = versionService.getTimeline(parseId(projectId));
        return ResponseEntity.ok(ApiResponse.<TimelineDTO>builder()
                .status(200).message("Timeline retrieved.").data(result).build());
    }

    @GetMapping("/{projectId}/snapshots")
    public ResponseEntity<ApiResponse<List<SnapshotDTO>>> listSnapshots(@PathVariable String projectId) {
        List<SnapshotDTO> result = versionService.listSnapshots(parseId(projectId));
        return ResponseEntity.ok(ApiResponse.<List<SnapshotDTO>>builder()
                .status(200).message("Snapshots retrieved.").data(result).build());
    }

    @GetMapping("/snapshots/{snapId}")
    public ResponseEntity<ApiResponse<SnapshotDTO>> getSnapshot(@PathVariable String snapId) {
        SnapshotDTO result = versionService.getSnapshot(parseId(snapId));
        return ResponseEntity.ok(ApiResponse.<SnapshotDTO>builder()
                .status(200).message("Snapshot retrieved.").data(result).build());
    }

    private UUID parseId(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            throw new IllegalArgumentException("Id is missing");
        }
        String s = rawId.trim();
        if (s.startsWith("0x") || s.startsWith("0X")) {
            s = s.substring(2);
        }
        if (s.length() == 32 && !s.contains("-")) {
            s = s.substring(0, 8) + "-" + s.substring(8, 12) + "-"
                    + s.substring(12, 16) + "-" + s.substring(16, 20) + "-"
                    + s.substring(20);
        }
        return UUID.fromString(s);
    }
}
