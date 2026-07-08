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
@Table(name = "code_snapshots")
@PrimaryKeyJoinColumn(name = "snapshot_id")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class CodeSnapshot extends Snapshot {

    @Column(name = "code_id", nullable = false)
    private UUID codeId;

    @Column(name = "code_layer", length = 50)
    private String codeLayer;
}
