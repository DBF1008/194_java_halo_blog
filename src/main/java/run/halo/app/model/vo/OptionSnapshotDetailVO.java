package run.halo.app.model.vo;

import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.model.dto.OptionSnapshotDTO;

/**
 * Option snapshot detail vo, including the captured raw option data.
 *
 * @author halo
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class OptionSnapshotDetailVO extends OptionSnapshotDTO {

    /**
     * Captured raw options as {@code optionKey -> optionValue}.
     */
    private Map<String, String> data;
}
