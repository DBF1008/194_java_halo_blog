package run.halo.app.model.dto;

import java.util.Date;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Snapshot data stored as JSON file.
 *
 * @author halo
 * @date 2024-01-01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotData {

    private String name;

    private Date createTime;

    /**
     * Raw DB values (option key to string value).
     */
    private Map<String, String> options;
}
