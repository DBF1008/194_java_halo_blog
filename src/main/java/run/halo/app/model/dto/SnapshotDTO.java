package run.halo.app.model.dto;

import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Snapshot output DTO for API responses.
 *
 * @author halo
 * @date 2024-01-01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotDTO {

    private String name;

    private Date createTime;

    private int optionCount;
}
