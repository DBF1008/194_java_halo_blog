package run.halo.app.service;

import java.io.IOException;
import org.springframework.lang.NonNull;
import org.springframework.web.multipart.MultipartFile;
import run.halo.app.model.dto.BackupPreviewDTO;
import run.halo.app.model.dto.DataImportConflictDTO;
import run.halo.app.service.BackupService.BackupType;

/**
 * Backup preview service interface.
 * Provides read-only analysis of backup files without any side effects.
 *
 * @author ryanwang
 */
public interface BackupPreviewService {

    /**
     * Preview an existing backup file on disk.
     *
     * @param filename backup filename (not path)
     * @param type backup type to determine base directory
     * @return preview DTO with content summary
     */
    @NonNull
    BackupPreviewDTO previewExistingBackup(@NonNull String filename, @NonNull BackupType type);

    /**
     * Preview an uploaded file without persisting anything.
     *
     * @param file uploaded file
     * @param type backup type hint (determines parsing strategy)
     * @return preview DTO with content summary
     * @throws IOException if reading the uploaded file fails
     */
    @NonNull
    BackupPreviewDTO previewUploadedBackup(@NonNull MultipartFile file, @NonNull BackupType type)
        throws IOException;

    /**
     * Preview data import: entity counts + conflict analysis against current
     * DB. Works for JSON_DATA type only.
     *
     * @param filename existing JSON data export filename on disk
     * @return conflict analysis DTO
     * @throws IOException if reading the file fails
     */
    @NonNull
    DataImportConflictDTO previewDataImport(@NonNull String filename) throws IOException;

    /**
     * Preview data import from uploaded file.
     *
     * @param file uploaded JSON data file
     * @return conflict analysis DTO
     * @throws IOException if reading the file fails
     */
    @NonNull
    DataImportConflictDTO previewDataImportFromUpload(@NonNull MultipartFile file)
        throws IOException;
}
