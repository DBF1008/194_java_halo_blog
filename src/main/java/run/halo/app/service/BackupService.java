package run.halo.app.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.springframework.core.io.Resource;
import org.springframework.lang.NonNull;
import org.springframework.web.multipart.MultipartFile;
import run.halo.app.model.dto.BackupDTO;
import run.halo.app.model.dto.BackupManifestDTO;
import run.halo.app.model.dto.post.BasePostDetailDTO;
import run.halo.app.model.params.PostMarkdownParam;

/**
 * Backup service interface.
 *
 * @author johnniang
 * @author ryanwang
 * @date 2019-04-26
 */
public interface BackupService {

    /**
     * Import markdown content.
     *
     * @param file file
     * @return base post detail dto
     * @throws IOException throws IOException
     */
    BasePostDetailDTO importMarkdown(MultipartFile file) throws IOException;

    /**
     * Zips work directory.
     *
     * @return backup dto.
     */
    @NonNull
    BackupDTO backupWorkDirectory();


    /**
     * Lists all backups.
     *
     * @return backup list
     */
    @NonNull
    List<BackupDTO> listWorkDirBackups();

    /**
     * Get backup data by backup file name.
     *
     * @param backupFileName backup file name must not be blank
     * @param type backup type must not be null
     * @return an optional of backup data
     */
    @NonNull
    Optional<BackupDTO> getBackup(@NonNull Path backupFileName, @NonNull BackupType type);

    /**
     * Builds a read-only manifest (dry-run preview) for a backup file already stored on the
     * server.
     *
     * <p>This method never extracts the archive, writes to the database or modifies the work
     * directory. It is safe to invoke before downloading or importing a backup.</p>
     *
     * @param backupFilePath path of the backup file must not be null
     * @param type backup type must not be null
     * @return manifest describing the backup content and its potential impact on the current site
     */
    @NonNull
    BackupManifestDTO previewBackup(@NonNull Path backupFilePath, @NonNull BackupType type);

    /**
     * Builds a read-only manifest (dry-run preview) for an uploaded backup file.
     *
     * <p>The uploaded file is only analysed in memory; this method never persists the file,
     * extracts the archive, writes to the database or modifies the work directory.</p>
     *
     * @param file uploaded backup file must not be null
     * @param type backup type must not be null
     * @return manifest describing the backup content and its potential impact on the current site
     */
    @NonNull
    BackupManifestDTO previewUploadedBackup(@NonNull MultipartFile file, @NonNull BackupType type);

    /**
     * Deletes backup.
     *
     * @param fileName filename must not be blank
     */
    void deleteWorkDirBackup(@NonNull String fileName);

    /**
     * Loads file as resource.
     *
     * @param fileName backup file name must not be blank.
     * @param basePath base path
     * @return resource of the given file
     */
    @NonNull
    Resource loadFileAsResource(@NonNull String basePath, @NonNull String fileName);


    /**
     * Export all database's data.
     *
     * @return data
     */
    @NonNull
    BackupDTO exportData();

    /**
     * List all exported data.
     *
     * @return list of backup dto
     */
    List<BackupDTO> listExportedData();

    /**
     * Deletes exported data.
     *
     * @param fileName fileName
     */
    void deleteExportedData(@NonNull String fileName);

    /**
     * Import data
     *
     * @param file file
     * @throws IOException throws IOException
     */
    void importData(MultipartFile file) throws IOException;

    /**
     * Export Markdown content
     *
     * @param postMarkdownParam param
     * @return backup dto.
     * @throws IOException throws IOException
     */
    @NonNull
    BackupDTO exportMarkdowns(PostMarkdownParam postMarkdownParam) throws IOException;

    /**
     * list Markdown backups
     *
     * @return backup list
     */
    @NonNull
    List<BackupDTO> listMarkdowns();

    /**
     * delete a markdown backup.
     *
     * @param fileName file name
     */
    void deleteMarkdown(@NonNull String fileName);

    /**
     * Backup type.
     *
     * @author johnniang
     */
    enum BackupType {
        WHOLE_SITE("/api/admin/backups/work-dir"),
        JSON_DATA("/api/admin/backups/data"),
        MARKDOWN("/api/admin/backups/markdown/export"),
        ;

        private final String baseUri;

        BackupType(String baseUri) {
            this.baseUri = baseUri;
        }

        public String getBaseUri() {
            return baseUri;
        }
    }
}
