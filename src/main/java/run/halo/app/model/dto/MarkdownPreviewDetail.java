package run.halo.app.model.dto;

import java.util.List;
import lombok.Data;

/**
 * Markdown backup preview detail.
 *
 * @author ryanwang
 */
@Data
public class MarkdownPreviewDetail {

    /**
     * Number of .md files found.
     */
    private Integer postCount;

    /**
     * Whether an upload/ directory is included in the zip.
     */
    private Boolean hasUploadDir;

    /**
     * Number of files in upload/ if present.
     */
    private Integer uploadFileCount;

    /**
     * Sample of first N markdown filenames (for display).
     */
    private List<String> samplePostTitles;
}
