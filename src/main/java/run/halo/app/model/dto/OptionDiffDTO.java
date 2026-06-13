package run.halo.app.model.dto;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Diff result between a snapshot and current options.
 *
 * @author halo
 * @date 2024-01-01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OptionDiffDTO {

    private String snapshotName;

    /**
     * Keys present in snapshot but not in current options.
     */
    private Map<String, String> added;

    /**
     * Keys present in current options but not in snapshot.
     */
    private Map<String, String> removed;

    /**
     * Keys present in both but with different values.
     */
    private Map<String, DiffEntry> changed;

    /**
     * Represents a value difference for a single option key.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiffEntry {

        private String snapshotValue;

        private String currentValue;
    }
}
