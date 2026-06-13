package run.halo.app.model.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.GenericGenerator;

/**
 * Option snapshot entity.
 *
 * <p>Stores a named, point-in-time copy of the raw persisted options so that the
 * configuration can later be compared, partially restored or rolled back as a whole.
 *
 * @author halo
 */
@Data
@Entity
@Table(name = "option_snapshots",
    uniqueConstraints = @UniqueConstraint(name = "uniq_option_snapshot_name",
        columnNames = "name"))
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class OptionSnapshot extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY, generator = "custom-id")
    @GenericGenerator(name = "custom-id",
        strategy = "run.halo.app.model.entity.support.CustomIdGenerator")
    private Integer id;

    /**
     * Snapshot name (unique).
     */
    @Column(name = "name", length = 255, nullable = false)
    private String name;

    /**
     * Optional description.
     */
    @Column(name = "description", length = 1023)
    private String description;

    /**
     * Captured options serialized as a JSON object of {@code optionKey -> optionValue}.
     */
    @Column(name = "snapshot_data", nullable = false)
    @Lob
    private String data;
}
