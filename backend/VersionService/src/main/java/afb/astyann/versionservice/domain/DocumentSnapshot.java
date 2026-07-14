package afb.astyann.versionservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Table(name = "document_snapshots")
@PrimaryKeyJoinColumn(name = "snapshot_id")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class DocumentSnapshot extends Snapshot {

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "document_type", length = 50)
    private String documentType;
}
