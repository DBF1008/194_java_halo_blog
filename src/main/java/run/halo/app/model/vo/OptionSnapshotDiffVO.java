package run.halo.app.model.vo;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import run.halo.app.model.enums.OptionDiffType;

/**
 * Result of comparing a snapshot against the current (raw, persisted) configuration.
 *
 * @author halo
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OptionSnapshotDiffVO {

    private Integer snapshotId;

    private String snapshotName;

    private int added;

    private int deleted;

    private int modified;

    private int unchanged;

    private List<DiffItem> items;

    /**
     * A single option key difference.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiffItem {

        private String key;

        /**
         * Value stored in the snapshot, or {@code null} if the key is not in the snapshot.
         */
        private String snapshotValue;

        /**
         * Value currently persisted, or {@code null} if the key is not currently persisted.
         */
        private String currentValue;

        private OptionDiffType type;
    }
}
