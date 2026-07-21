package afb.astyann.codegeneration.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Snapshot of a {@link GeneratedCode}'s live artifact at the moment it was approved. Captured
 * so {@code activateVersion} can restore any previously-approved ZIP as the current live
 * artifact — {@link GeneratedCode} itself only ever holds the current live content.
 */
@Entity
@Table(name = "code_version_archive")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeVersionArchive {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "archive_id", updatable = false, nullable = false)
    private UUID archiveId;

    @Column(name = "snapshot_id", nullable = false)
    private UUID snapshotId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "code_id", nullable = false)
    private UUID codeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CodeLayer layer;

    @Column(name = "code_path")
    private String codePath;
}
