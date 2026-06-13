package run.halo.app.controller.admin.api;

import static run.halo.app.service.BackupService.BackupType.JSON_DATA;
import static run.halo.app.service.BackupService.BackupType.MARKDOWN;
import static run.halo.app.service.BackupService.BackupType.WHOLE_SITE;

import io.swagger.annotations.ApiOperation;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import run.halo.app.model.dto.BackupPreviewDTO;
import run.halo.app.model.dto.DataImportConflictDTO;
import run.halo.app.service.BackupPreviewService;

/**
 * Backup preview controller.
 * Provides read-only preview endpoints for backup files.
 *
 * @author ryanwang
 */
@RestController
@RequestMapping("/api/admin/backups/preview")
@Slf4j
public class BackupPreviewController {

    private final BackupPreviewService backupPreviewService;

    public BackupPreviewController(BackupPreviewService backupPreviewService) {
        this.backupPreviewService = backupPreviewService;
    }

    // ====== Preview existing backups on disk ======

    @GetMapping("work-dir")
    @ApiOperation("Preview a work-dir backup file content")
    public BackupPreviewDTO previewWorkDirBackup(
        @RequestParam("filename") String filename) {
        return backupPreviewService.previewExistingBackup(filename, WHOLE_SITE);
    }

    @GetMapping("data")
    @ApiOperation("Preview a JSON data export file content")
    public BackupPreviewDTO previewDataBackup(
        @RequestParam("filename") String filename) {
        return backupPreviewService.previewExistingBackup(filename, JSON_DATA);
    }

    @GetMapping("markdown")
    @ApiOperation("Preview a markdown export file content")
    public BackupPreviewDTO previewMarkdownBackup(
        @RequestParam("filename") String filename) {
        return backupPreviewService.previewExistingBackup(filename, MARKDOWN);
    }

    // ====== Preview uploaded files (read-only) ======

    @PostMapping("upload/work-dir")
    @ApiOperation("Preview an uploaded work-dir backup without saving")
    public BackupPreviewDTO previewUploadedWorkDir(
        @RequestPart("file") MultipartFile file) throws IOException {
        return backupPreviewService.previewUploadedBackup(file, WHOLE_SITE);
    }

    @PostMapping("upload/data")
    @ApiOperation("Preview an uploaded JSON data file without saving")
    public BackupPreviewDTO previewUploadedData(
        @RequestPart("file") MultipartFile file) throws IOException {
        return backupPreviewService.previewUploadedBackup(file, JSON_DATA);
    }

    @PostMapping("upload/markdown")
    @ApiOperation("Preview an uploaded markdown zip without saving")
    public BackupPreviewDTO previewUploadedMarkdown(
        @RequestPart("file") MultipartFile file) throws IOException {
        return backupPreviewService.previewUploadedBackup(file, MARKDOWN);
    }

    // ====== Data import conflict preview ======

    @GetMapping("data/conflicts")
    @ApiOperation("Preview data import conflicts for an existing export file")
    public DataImportConflictDTO previewDataImportConflicts(
        @RequestParam("filename") String filename) throws IOException {
        return backupPreviewService.previewDataImport(filename);
    }

    @PostMapping("upload/data/conflicts")
    @ApiOperation("Preview data import conflicts for an uploaded file")
    public DataImportConflictDTO previewUploadedDataImportConflicts(
        @RequestPart("file") MultipartFile file) throws IOException {
        return backupPreviewService.previewDataImportFromUpload(file);
    }
}
