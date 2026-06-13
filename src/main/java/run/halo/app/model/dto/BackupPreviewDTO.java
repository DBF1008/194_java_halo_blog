package run.halo.app.model.dto;

import lombok.Data;

/**
 * Backup preview top-level DTO.
 *
 * @author ryanwang
 */
@Data
public class BackupPreviewDTO {

    /**
     * Backup type: WHOLE_SITE, JSON_DATA, MARKDOWN.
     */
    private String backupType;

    /**
     * Filename being previewed (null for upload-preview).
     */
    private String filename;

    /**
     * File size in bytes.
     */
    private Long fileSize;

    /**
     * Work-dir zip analysis detail (non-null when backupType is WHOLE_SITE).
     */
    private WorkDirPreviewDetail workDirDetail;

    /**
     * JSON data analysis detail (non-null when backupType is JSON_DATA).
     */
    private JsonDataPreviewDetail jsonDataDetail;

    /**
     * Markdown zip analysis detail (non-null when backupType is MARKDOWN).
     */
    private MarkdownPreviewDetail markdownDetail;
}
