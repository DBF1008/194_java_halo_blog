package run.halo.app.model.dto;

import java.util.List;
import lombok.Data;

/**
 * Work-dir backup preview detail.
 *
 * @author ryanwang
 */
@Data
public class WorkDirPreviewDetail {

    /**
     * Top-level directory/file names found in the zip.
     */
    private List<String> topLevelEntries;

    /**
     * Total number of files in the zip.
     */
    private Integer totalFileCount;

    /**
     * Total uncompressed size in bytes.
     */
    private Long totalUncompressedSize;

    /**
     * Whether a database directory (db/) is present.
     */
    private Boolean hasDatabase;

    /**
     * Whether a themes directory is present.
     */
    private Boolean hasThemes;

    /**
     * Whether an upload directory is present.
     */
    private Boolean hasUploads;

    /**
     * List of theme folder names if themes/ is present.
     */
    private List<String> themeNames;
}
