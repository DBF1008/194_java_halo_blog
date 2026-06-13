package run.halo.app.model.params;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import lombok.Data;

/**
 * Option snapshot param used when creating a snapshot of the current configuration.
 *
 * @author halo
 */
@Data
public class OptionSnapshotParam {

    @NotBlank(message = "Snapshot name must not be blank")
    @Size(max = 255, message = "Length of snapshot name must not be more than {max}")
    private String name;

    @Size(max = 1023, message = "Length of snapshot description must not be more than {max}")
    private String description;
}
