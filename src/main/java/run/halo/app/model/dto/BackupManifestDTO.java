package run.halo.app.model.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import run.halo.app.service.BackupService.BackupType;

/**
 * Read-only manifest (dry-run preview) of a backup package.
 *
 * <p>Describes what a backup contains and how importing it might affect the current site, without
 * extracting the archive, writing to the database or touching the work directory.</p>
 *
 * @author halo
 */
@Data
public class BackupManifestDTO {

    /**
     * Type of the inspected backup.
     */
    private BackupType backupType;

    /**
     * Name of the backup file (the stored file name, or the original name of an uploaded file).
     */
    private String filename;

    /**
     * Size of the backup file in bytes.
     */
    private Long fileSize;

    /**
     * Whether the manifest was produced from a user uploaded file (read-only analysis) instead of
     * a backup already stored on the server.
     */
    private boolean fromUpload;

    /**
     * Manifest of a JSON data backup. {@code null} for archive backups.
     */
    private DataManifest data;

    /**
     * Manifest of an archive (work directory or markdown) backup. {@code null} for data backups.
     */
    private ArchiveManifest archive;

    /**
     * Human readable notes about the potential impact of importing this backup.
     */
    private List<String> warnings = new ArrayList<>();

    /**
     * Manifest of a JSON data backup.
     */
    @Data
    public static class DataManifest {

        /**
         * Halo version recorded in the backup.
         */
        private String version;

        /**
         * Export date recorded in the backup.
         */
        private String exportDate;

        /**
         * Number of records per table contained in the backup, keyed by the table name used in the
         * export file (e.g. {@code posts}, {@code sheets}, {@code post_comments}).
         */
        private Map<String, Integer> contentCounts = new LinkedHashMap<>();

        /**
         * Primary key conflicts that may occur when importing core content tables.
         */
        private List<TableConflict> conflicts = new ArrayList<>();

        /**
         * Option keys present in the backup that already exist on the current site. Importing the
         * backup would re-insert these keys and may overwrite key configuration.
         */
        private List<String> conflictingOptionKeys = new ArrayList<>();

        /**
         * Whether importing the backup user could conflict with an already existing user.
         */
        private boolean userConflict;
    }

    /**
     * Manifest of an archive (zip) backup.
     */
    @Data
    public static class ArchiveManifest {

        /**
         * Total number of entries inside the archive.
         */
        private int totalEntries;

        /**
         * Number of file entries inside the archive.
         */
        private int fileCount;

        /**
         * Number of directory entries inside the archive.
         */
        private int directoryCount;

        /**
         * Distinct top level entries inside the archive.
         */
        private List<String> topLevelEntries = new ArrayList<>();

        /**
         * Number of markdown files. Only populated for markdown backups.
         */
        private Integer markdownCount;

        /**
         * Whether the archive carries an {@code upload} directory. Only populated for markdown
         * backups.
         */
        private Boolean containsUpload;

        /**
         * Number of files inside the {@code upload} directory. Only populated for markdown backups.
         */
        private Integer uploadFileCount;
    }

    /**
     * Primary key conflict information for a single table.
     */
    @Data
    public static class TableConflict {

        /**
         * Table name as used in the export file.
         */
        private String table;

        /**
         * Number of records of this table inside the backup.
         */
        private int backupCount;

        /**
         * Number of records of this table currently stored on the site.
         */
        private int existingCount;

        /**
         * Number of primary keys present both in the backup and on the current site.
         */
        private int conflictCount;

        /**
         * A small sample of conflicting primary keys (for display purposes).
         */
        private List<String> sampleConflictIds = new ArrayList<>();

        public TableConflict() {
        }

        public TableConflict(String table) {
            this.table = table;
        }
    }
}
