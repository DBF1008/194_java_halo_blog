package run.halo.app.service.impl;

import static run.halo.app.model.support.HaloConst.HALO_BACKUP_MARKDOWN_PREFIX;
import static run.halo.app.model.support.HaloConst.HALO_BACKUP_PREFIX;
import static run.halo.app.model.support.HaloConst.HALO_DATA_EXPORT_PREFIX;
import static run.halo.app.utils.DateTimeUtils.HORIZONTAL_LINE_DATETIME_FORMATTER;
import static run.halo.app.utils.FileUtils.checkDirectoryTraversal;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.io.file.FileWriter;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.multipart.MultipartFile;
import run.halo.app.config.properties.HaloProperties;
import run.halo.app.event.options.OptionUpdatedEvent;
import run.halo.app.event.theme.ThemeUpdatedEvent;
import run.halo.app.exception.BadRequestException;
import run.halo.app.exception.NotFoundException;
import run.halo.app.exception.ServiceException;
import run.halo.app.handler.file.FileHandler;
import run.halo.app.model.dto.BackupDTO;
import run.halo.app.model.dto.BackupManifestDTO;
import run.halo.app.model.dto.post.BasePostDetailDTO;
import run.halo.app.model.entity.Attachment;
import run.halo.app.model.entity.Category;
import run.halo.app.model.entity.CommentBlackList;
import run.halo.app.model.entity.Journal;
import run.halo.app.model.entity.JournalComment;
import run.halo.app.model.entity.Link;
import run.halo.app.model.entity.Log;
import run.halo.app.model.entity.Menu;
import run.halo.app.model.entity.Option;
import run.halo.app.model.entity.Photo;
import run.halo.app.model.entity.Post;
import run.halo.app.model.entity.PostCategory;
import run.halo.app.model.entity.PostComment;
import run.halo.app.model.entity.PostMeta;
import run.halo.app.model.entity.PostTag;
import run.halo.app.model.entity.Sheet;
import run.halo.app.model.entity.SheetComment;
import run.halo.app.model.entity.SheetMeta;
import run.halo.app.model.entity.Tag;
import run.halo.app.model.entity.ThemeSetting;
import run.halo.app.model.entity.User;
import run.halo.app.model.params.PostMarkdownParam;
import run.halo.app.model.support.HaloConst;
import run.halo.app.model.vo.PostMarkdownVO;
import run.halo.app.security.service.OneTimeTokenService;
import run.halo.app.service.AttachmentService;
import run.halo.app.service.BackupService;
import run.halo.app.service.CategoryService;
import run.halo.app.service.CommentBlackListService;
import run.halo.app.service.JournalCommentService;
import run.halo.app.service.JournalService;
import run.halo.app.service.LinkService;
import run.halo.app.service.LogService;
import run.halo.app.service.MenuService;
import run.halo.app.service.OptionService;
import run.halo.app.service.PhotoService;
import run.halo.app.service.PostCategoryService;
import run.halo.app.service.PostCommentService;
import run.halo.app.service.PostMetaService;
import run.halo.app.service.PostService;
import run.halo.app.service.PostTagService;
import run.halo.app.service.SheetCommentService;
import run.halo.app.service.SheetMetaService;
import run.halo.app.service.SheetService;
import run.halo.app.service.TagService;
import run.halo.app.service.ThemeSettingService;
import run.halo.app.service.UserService;
import run.halo.app.utils.DateTimeUtils;
import run.halo.app.utils.HaloUtils;
import run.halo.app.utils.JsonUtils;

/**
 * Backup service implementation.
 *
 * @author johnniang
 * @author ryanwang
 * @author Raremaa
 * @date 2019-04-26
 */
@Service
@Slf4j
public class BackupServiceImpl implements BackupService {

    private static final String BACKUP_RESOURCE_BASE_URI = "/api/admin/backups/work-dir";

    private static final String DATA_EXPORT_MARKDOWN_BASE_URI =
        "/api/admin/backups/markdown/export";

    private static final String DATA_EXPORT_BASE_URI = "/api/admin/backups/data";

    private static final String UPLOAD_SUB_DIR = "upload/";

    private static final int CONFLICT_SAMPLE_LIMIT = 10;

    /**
     * Table names contained in a JSON data export, in export order.
     */
    private static final List<String> DATA_TABLE_KEYS = List.of(
        "attachments", "categories", "comment_black_list", "journals", "journal_comments",
        "links", "logs", "menus", "options", "photos", "posts", "post_categories",
        "post_comments", "post_metas", "post_tags", "sheets", "sheet_comments", "sheet_metas",
        "tags", "theme_settings", "user");

    private final AttachmentService attachmentService;

    private final CategoryService categoryService;

    private final CommentBlackListService commentBlackListService;

    private final JournalService journalService;

    private final JournalCommentService journalCommentService;

    private final LinkService linkService;

    private final LogService logService;

    private final MenuService menuService;

    private final OptionService optionService;

    private final PhotoService photoService;

    private final PostService postService;

    private final PostCategoryService postCategoryService;

    private final PostCommentService postCommentService;

    private final PostMetaService postMetaService;

    private final PostTagService postTagService;

    private final SheetService sheetService;

    private final SheetCommentService sheetCommentService;

    private final SheetMetaService sheetMetaService;

    private final TagService tagService;

    private final ThemeSettingService themeSettingService;

    private final UserService userService;

    private final OneTimeTokenService oneTimeTokenService;

    private final HaloProperties haloProperties;

    private final ApplicationEventPublisher eventPublisher;

    public BackupServiceImpl(AttachmentService attachmentService, CategoryService categoryService,
        CommentBlackListService commentBlackListService, JournalService journalService,
        JournalCommentService journalCommentService, LinkService linkService, LogService logService,
        MenuService menuService, OptionService optionService, PhotoService photoService,
        PostService postService, PostCategoryService postCategoryService,
        PostCommentService postCommentService, PostMetaService postMetaService,
        PostTagService postTagService, SheetService sheetService,
        SheetCommentService sheetCommentService, SheetMetaService sheetMetaService,
        TagService tagService, ThemeSettingService themeSettingService, UserService userService,
        OneTimeTokenService oneTimeTokenService, HaloProperties haloProperties,
        ApplicationEventPublisher eventPublisher) {
        this.attachmentService = attachmentService;
        this.categoryService = categoryService;
        this.commentBlackListService = commentBlackListService;
        this.journalService = journalService;
        this.journalCommentService = journalCommentService;
        this.linkService = linkService;
        this.logService = logService;
        this.menuService = menuService;
        this.optionService = optionService;
        this.photoService = photoService;
        this.postService = postService;
        this.postCategoryService = postCategoryService;
        this.postCommentService = postCommentService;
        this.postMetaService = postMetaService;
        this.postTagService = postTagService;
        this.sheetService = sheetService;
        this.sheetCommentService = sheetCommentService;
        this.sheetMetaService = sheetMetaService;
        this.tagService = tagService;
        this.themeSettingService = themeSettingService;
        this.userService = userService;
        this.oneTimeTokenService = oneTimeTokenService;
        this.haloProperties = haloProperties;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public BasePostDetailDTO importMarkdown(MultipartFile file) throws IOException {

        // Read markdown content.
        String markdown = IoUtil.read(file.getInputStream(), StandardCharsets.UTF_8);

        // TODO sheet import
        return postService.importMarkdown(markdown, file.getOriginalFilename());
    }

    @Override
    public BackupDTO backupWorkDirectory() {
        // Zip work directory to temporary file
        try {
            // Create zip path for halo zip
            String haloZipFileName = HALO_BACKUP_PREFIX
                + DateTimeUtils.format(LocalDateTime.now(), HORIZONTAL_LINE_DATETIME_FORMATTER)
                + IdUtil.simpleUUID().hashCode() + ".zip";
            // Create halo zip file
            Path haloZipFilePath = Paths.get(haloProperties.getBackupDir(), haloZipFileName);
            if (!Files.exists(haloZipFilePath.getParent())) {
                Files.createDirectories(haloZipFilePath.getParent());
            }
            Path haloZipPath = Files.createFile(haloZipFilePath);

            // Zip halo
            run.halo.app.utils.FileUtils
                .zip(Paths.get(this.haloProperties.getWorkDir()), haloZipPath);

            // Build backup dto
            return buildBackupDto(BACKUP_RESOURCE_BASE_URI, haloZipPath);
        } catch (IOException e) {
            throw new ServiceException("Failed to backup halo", e);
        }
    }

    @Override
    public List<BackupDTO> listWorkDirBackups() {
        // Ensure the parent folder exist
        Path backupParentPath = Paths.get(haloProperties.getBackupDir());
        if (Files.notExists(backupParentPath)) {
            return Collections.emptyList();
        }

        // Build backup dto
        try (Stream<Path> subPathStream = Files.list(backupParentPath)) {
            return subPathStream
                .filter(backupPath -> StringUtils
                    .startsWithIgnoreCase(backupPath.getFileName().toString(),
                        HALO_BACKUP_PREFIX))
                .map(backupPath -> buildBackupDto(BACKUP_RESOURCE_BASE_URI, backupPath))
                .sorted(Comparator.comparingLong(BackupDTO::getUpdateTime).reversed())
                .collect(Collectors.toList());
        } catch (IOException e) {
            throw new ServiceException("Failed to fetch backups", e);
        }
    }

    @Override
    public Optional<BackupDTO> getBackup(@NonNull Path backupFilePath, @NonNull BackupType type) {
        if (Files.notExists(backupFilePath)) {
            return Optional.empty();
        }

        BackupDTO backupDto = buildBackupDto(type.getBaseUri(), backupFilePath);
        return Optional.of(backupDto);
    }

    @Override
    public BackupManifestDTO previewBackup(Path backupFilePath, BackupType type) {
        Assert.notNull(backupFilePath, "Backup file path must not be null");
        Assert.notNull(type, "Backup type must not be null");

        if (Files.notExists(backupFilePath)) {
            throw new NotFoundException(
                "备份文件 " + backupFilePath.getFileName() + " 不存在或已删除！")
                .setErrorData(backupFilePath.getFileName().toString());
        }

        BackupManifestDTO manifest = new BackupManifestDTO();
        manifest.setBackupType(type);
        manifest.setFilename(backupFilePath.getFileName().toString());
        manifest.setFromUpload(false);
        try {
            manifest.setFileSize(Files.size(backupFilePath));
        } catch (IOException e) {
            throw new ServiceException("Failed to access backup file " + backupFilePath, e);
        }

        if (type == BackupType.JSON_DATA) {
            String content;
            try {
                content = new String(Files.readAllBytes(backupFilePath), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new ServiceException("Failed to read backup file " + backupFilePath, e);
            }
            manifest.setData(buildDataManifest(content));
        } else {
            try (InputStream inputStream = Files.newInputStream(backupFilePath)) {
                manifest.setArchive(buildArchiveManifest(inputStream, type));
            } catch (IOException e) {
                throw new ServiceException("Failed to read backup file " + backupFilePath, e);
            }
        }

        populateWarnings(manifest);
        return manifest;
    }

    @Override
    public BackupManifestDTO previewUploadedBackup(MultipartFile file, BackupType type) {
        Assert.notNull(file, "Upload file must not be null");
        Assert.notNull(type, "Backup type must not be null");

        if (file.isEmpty()) {
            throw new BadRequestException("上传的备份文件为空");
        }

        BackupManifestDTO manifest = new BackupManifestDTO();
        manifest.setBackupType(type);
        manifest.setFilename(file.getOriginalFilename());
        manifest.setFileSize(file.getSize());
        manifest.setFromUpload(true);

        try {
            if (type == BackupType.JSON_DATA) {
                String content = IoUtil.read(file.getInputStream(), StandardCharsets.UTF_8);
                manifest.setData(buildDataManifest(content));
            } else {
                try (InputStream inputStream = file.getInputStream()) {
                    manifest.setArchive(buildArchiveManifest(inputStream, type));
                }
            }
        } catch (IOException e) {
            throw new ServiceException("Failed to read uploaded backup file", e);
        }

        populateWarnings(manifest);
        return manifest;
    }

    @Override
    public void deleteWorkDirBackup(String fileName) {
        Assert.hasText(fileName, "File name must not be blank");

        Path backupRootPath = Paths.get(haloProperties.getBackupDir());

        // Get backup path
        Path backupPath = backupRootPath.resolve(fileName);

        // Check directory traversal
        checkDirectoryTraversal(backupRootPath, backupPath);

        try {
            // Delete backup file
            Files.delete(backupPath);
        } catch (NoSuchFileException e) {
            throw new NotFoundException("The file " + fileName + " was not found", e);
        } catch (IOException e) {
            throw new ServiceException("Failed to delete backup", e);
        }
    }

    @Override
    public Resource loadFileAsResource(String basePath, String fileName) {
        Assert.hasText(basePath, "Base path must not be blank");
        Assert.hasText(fileName, "Backup file name must not be blank");

        Path backupParentPath = Paths.get(basePath);

        try {
            if (Files.notExists(backupParentPath)) {
                // Create backup parent path if it does not exists
                Files.createDirectories(backupParentPath);
            }

            // Get backup file path
            Path backupFilePath = Paths.get(basePath, fileName).normalize();

            // Check directory traversal
            checkDirectoryTraversal(backupParentPath, backupFilePath);

            // Build url resource
            Resource backupResource = new UrlResource(backupFilePath.toUri());
            if (!backupResource.exists()) {
                // If the backup resource is not exist
                throw new NotFoundException("The file " + fileName + " was not found");
            }
            // Return the backup resource
            return backupResource;
        } catch (MalformedURLException e) {
            throw new NotFoundException("The file " + fileName + " was not found", e);
        } catch (IOException e) {
            throw new ServiceException("Failed to create backup parent path: " + backupParentPath,
                e);
        }
    }

    @Override
    public BackupDTO exportData() {
        Map<String, Object> data = new HashMap<>();
        data.put("version", HaloConst.HALO_VERSION);
        data.put("export_date", DateUtil.now());
        data.put("attachments", attachmentService.listAll());
        data.put("categories", categoryService.listAll());
        data.put("comment_black_list", commentBlackListService.listAll());
        data.put("journals", journalService.listAll());
        data.put("journal_comments", journalCommentService.listAll());
        data.put("links", linkService.listAll());
        data.put("logs", logService.listAll());
        data.put("menus", menuService.listAll());
        data.put("options", optionService.listAll());
        data.put("photos", photoService.listAll());
        data.put("posts", postService.listAll());
        data.put("post_categories", postCategoryService.listAll());
        data.put("post_comments", postCommentService.listAll());
        data.put("post_metas", postMetaService.listAll());
        data.put("post_tags", postTagService.listAll());
        data.put("sheets", sheetService.listAll());
        data.put("sheet_comments", sheetCommentService.listAll());
        data.put("sheet_metas", sheetMetaService.listAll());
        data.put("tags", tagService.listAll());
        data.put("theme_settings", themeSettingService.listAll());
        data.put("user", userService.listAll());

        try {
            String haloDataFileName = HALO_DATA_EXPORT_PREFIX
                + DateTimeUtils.format(LocalDateTime.now(), HORIZONTAL_LINE_DATETIME_FORMATTER)
                + IdUtil.simpleUUID().hashCode() + ".json";

            Path haloDataFilePath = Paths.get(haloProperties.getDataExportDir(), haloDataFileName);
            if (!Files.exists(haloDataFilePath.getParent())) {
                Files.createDirectories(haloDataFilePath.getParent());
            }
            Path haloDataPath = Files.createFile(haloDataFilePath);

            FileWriter fileWriter = new FileWriter(haloDataPath.toFile(), CharsetUtil.UTF_8);
            fileWriter.write(JsonUtils.objectToJson(data));

            return buildBackupDto(DATA_EXPORT_BASE_URI, haloDataPath);
        } catch (IOException e) {
            throw new ServiceException("导出数据失败", e);
        }
    }

    @Override
    public List<BackupDTO> listExportedData() {

        Path exportedDataParentPath = Paths.get(haloProperties.getDataExportDir());
        if (Files.notExists(exportedDataParentPath)) {
            return Collections.emptyList();
        }

        try (Stream<Path> subPathStream = Files.list(exportedDataParentPath)) {
            return subPathStream
                .filter(backupPath -> StringUtils
                    .startsWithIgnoreCase(backupPath.getFileName().toString(),
                        HALO_DATA_EXPORT_PREFIX))
                .map(backupPath -> buildBackupDto(DATA_EXPORT_BASE_URI, backupPath))
                .sorted(Comparator.comparingLong(BackupDTO::getUpdateTime).reversed())
                .collect(Collectors.toList());
        } catch (IOException e) {
            throw new ServiceException("Failed to fetch exported data", e);
        }
    }

    @Override
    public void deleteExportedData(String fileName) {
        Assert.hasText(fileName, "File name must not be blank");

        Path dataExportRootPath = Paths.get(haloProperties.getDataExportDir());

        Path backupPath = dataExportRootPath.resolve(fileName);

        checkDirectoryTraversal(dataExportRootPath, backupPath);

        try {
            // Delete backup file
            Files.delete(backupPath);
        } catch (NoSuchFileException e) {
            throw new NotFoundException("The file " + fileName + " was not found", e);
        } catch (IOException e) {
            throw new ServiceException("Failed to delete backup", e);
        }
    }

    @Override
    public void importData(MultipartFile file) throws IOException {
        String jsonContent = IoUtil.read(file.getInputStream(), StandardCharsets.UTF_8);

        ObjectMapper mapper = JsonUtils.createDefaultJsonMapper();
        TypeReference<HashMap<String, Object>> typeRef =
            new TypeReference<>() {
            };
        HashMap<String, Object> data = mapper.readValue(jsonContent, typeRef);

        List<Attachment> attachments = Arrays.asList(mapper
            .readValue(mapper.writeValueAsString(data.get("attachments")), Attachment[].class));
        attachmentService.createInBatch(attachments);

        List<Category> categories = Arrays.asList(
            mapper.readValue(mapper.writeValueAsString(data.get("categories")), Category[].class));
        categoryService.createInBatch(categories);

        List<Tag> tags = Arrays
            .asList(mapper.readValue(mapper.writeValueAsString(data.get("tags")), Tag[].class));
        tagService.createInBatch(tags);

        List<CommentBlackList> commentBlackList = Arrays.asList(mapper
            .readValue(mapper.writeValueAsString(data.get("comment_black_list")),
                CommentBlackList[].class));
        commentBlackListService.createInBatch(commentBlackList);

        List<Journal> journals = Arrays.asList(
            mapper.readValue(mapper.writeValueAsString(data.get("journals")), Journal[].class));
        journalService.createInBatch(journals);

        List<JournalComment> journalComments = Arrays.asList(mapper
            .readValue(mapper.writeValueAsString(data.get("journal_comments")),
                JournalComment[].class));
        journalCommentService.createInBatch(journalComments);

        List<Link> links = Arrays
            .asList(mapper.readValue(mapper.writeValueAsString(data.get("links")), Link[].class));
        linkService.createInBatch(links);

        List<Log> logs = Arrays
            .asList(mapper.readValue(mapper.writeValueAsString(data.get("logs")), Log[].class));
        logService.createInBatch(logs);

        List<Menu> menus = Arrays
            .asList(mapper.readValue(mapper.writeValueAsString(data.get("menus")), Menu[].class));
        menuService.createInBatch(menus);

        List<Option> options = Arrays.asList(
            mapper.readValue(mapper.writeValueAsString(data.get("options")), Option[].class));
        optionService.createInBatch(options);

        eventPublisher.publishEvent(new OptionUpdatedEvent(this));

        List<Photo> photos = Arrays
            .asList(mapper.readValue(mapper.writeValueAsString(data.get("photos")), Photo[].class));
        photoService.createInBatch(photos);

        List<Post> posts = Arrays
            .asList(mapper.readValue(mapper.writeValueAsString(data.get("posts")), Post[].class));
        postService.createInBatch(posts);

        List<PostCategory> postCategories = Arrays.asList(mapper
            .readValue(mapper.writeValueAsString(data.get("post_categories")),
                PostCategory[].class));
        postCategoryService.createInBatch(postCategories);

        List<PostComment> postComments = Arrays.asList(mapper
            .readValue(mapper.writeValueAsString(data.get("post_comments")), PostComment[].class));
        postCommentService.createInBatch(postComments);

        List<PostMeta> postMetas = Arrays.asList(
            mapper.readValue(mapper.writeValueAsString(data.get("post_metas")), PostMeta[].class));
        postMetaService.createInBatch(postMetas);

        List<PostTag> postTags = Arrays.asList(
            mapper.readValue(mapper.writeValueAsString(data.get("post_tags")), PostTag[].class));
        postTagService.createInBatch(postTags);

        List<Sheet> sheets = Arrays
            .asList(mapper.readValue(mapper.writeValueAsString(data.get("sheets")), Sheet[].class));
        sheetService.createInBatch(sheets);

        List<SheetComment> sheetComments = Arrays.asList(mapper
            .readValue(mapper.writeValueAsString(data.get("sheet_comments")),
                SheetComment[].class));
        sheetCommentService.createInBatch(sheetComments);

        List<SheetMeta> sheetMetas = Arrays.asList(mapper
            .readValue(mapper.writeValueAsString(data.get("sheet_metas")), SheetMeta[].class));
        sheetMetaService.createInBatch(sheetMetas);

        List<ThemeSetting> themeSettings = Arrays.asList(mapper
            .readValue(mapper.writeValueAsString(data.get("theme_settings")),
                ThemeSetting[].class));
        themeSettingService.createInBatch(themeSettings);

        eventPublisher.publishEvent(new ThemeUpdatedEvent(this));

        List<User> users = Arrays.asList(mapper
            .readValue(mapper.writeValueAsString(data.get("user")),
                User[].class));

        if (users.size() > 0) {
            userService.create(users.get(0));
        }
    }

    @Override
    public BackupDTO exportMarkdowns(PostMarkdownParam postMarkdownParam) throws IOException {
        // Query all Post data
        List<PostMarkdownVO> postMarkdownList = postService.listPostMarkdowns();
        Assert.notEmpty(postMarkdownList, "当前无文章可以导出");

        // Write files to the temporary directory
        String markdownFileTempPathName =
            haloProperties.getBackupMarkdownDir() + IdUtil.simpleUUID().hashCode();
        for (PostMarkdownVO postMarkdownVo : postMarkdownList) {
            StringBuilder content = new StringBuilder();
            Boolean needFrontMatter =
                Optional.ofNullable(postMarkdownParam.getNeedFrontMatter()).orElse(false);
            if (needFrontMatter) {
                // Add front-matter
                content.append(postMarkdownVo.getFrontMatter()).append("\n");
            }
            content.append(postMarkdownVo.getOriginalContent());
            try {
                String markdownFileName =
                    postMarkdownVo.getTitle() + "-" + postMarkdownVo.getSlug() + ".md";
                Path markdownFilePath = Paths.get(markdownFileTempPathName, markdownFileName);
                if (!Files.exists(markdownFilePath.getParent())) {
                    Files.createDirectories(markdownFilePath.getParent());
                }
                Path markdownDataPath = Files.createFile(markdownFilePath);
                FileWriter fileWriter =
                    new FileWriter(markdownDataPath.toFile(), CharsetUtil.UTF_8);
                fileWriter.write(content.toString());
            } catch (IOException e) {
                throw new ServiceException("导出数据失败", e);
            }
        }

        // Create zip path
        String markdownZipFileName = HALO_BACKUP_MARKDOWN_PREFIX
            + DateTimeUtils.format(LocalDateTime.now(), HORIZONTAL_LINE_DATETIME_FORMATTER)
            + IdUtil.simpleUUID().hashCode() + ".zip";

        // Create zip file
        Path markdownZipFilePath =
            Paths.get(haloProperties.getBackupMarkdownDir(), markdownZipFileName);
        if (!Files.exists(markdownZipFilePath.getParent())) {
            Files.createDirectories(markdownZipFilePath.getParent());
        }
        Path markdownZipPath = Files.createFile(markdownZipFilePath);
        // Zip file
        try (ZipOutputStream markdownZipOut = new ZipOutputStream(
            Files.newOutputStream(markdownZipPath))) {

            // Zip temporary directory
            Path markdownFileTempPath = Paths.get(markdownFileTempPathName);
            run.halo.app.utils.FileUtils.zip(markdownFileTempPath, markdownZipOut);

            // Zip upload sub-directory
            String uploadPathName =
                FileHandler.normalizeDirectory(haloProperties.getWorkDir()) + UPLOAD_SUB_DIR;
            Path uploadPath = Paths.get(uploadPathName);
            if (Files.exists(uploadPath)) {
                run.halo.app.utils.FileUtils.zip(uploadPath, markdownZipOut);
            }

            // Remove files in the temporary directory
            run.halo.app.utils.FileUtils.deleteFolder(markdownFileTempPath);

            // Build backup dto
            return buildBackupDto(DATA_EXPORT_MARKDOWN_BASE_URI, markdownZipPath);
        } catch (IOException e) {
            throw new ServiceException("Failed to export markdowns", e);
        }
    }

    @Override
    public List<BackupDTO> listMarkdowns() {
        // Ensure the parent folder exist
        Path backupParentPath = Paths.get(haloProperties.getBackupMarkdownDir());
        if (Files.notExists(backupParentPath)) {
            return Collections.emptyList();
        }

        // Build backup dto
        try (Stream<Path> subPathStream = Files.list(backupParentPath)) {
            return subPathStream
                .filter(backupPath -> StringUtils
                    .startsWithIgnoreCase(backupPath.getFileName().toString(),
                        HALO_BACKUP_MARKDOWN_PREFIX))
                .map(backupPath -> buildBackupDto(DATA_EXPORT_MARKDOWN_BASE_URI, backupPath))
                .sorted(Comparator.comparingLong(BackupDTO::getUpdateTime).reversed())
                .collect(Collectors.toList());
        } catch (IOException e) {
            throw new ServiceException("Failed to fetch backups", e);
        }
    }

    @Override
    public void deleteMarkdown(String filename) {
        Assert.hasText(filename, "File name must not be blank");

        Path backupRootPath = Paths.get(haloProperties.getBackupMarkdownDir());

        // Get backup path
        Path backupPath = backupRootPath.resolve(filename);

        // Check directory traversal
        checkDirectoryTraversal(backupRootPath, backupPath);

        try {
            // Delete backup file
            Files.delete(backupPath);
        } catch (NoSuchFileException e) {
            throw new NotFoundException("The file " + filename + " was not found", e);
        } catch (IOException e) {
            throw new ServiceException("Failed to delete backup", e);
        }
    }

    /**
     * Builds backup dto.
     *
     * @param backupPath backup path must not be null
     * @return backup dto
     */
    private BackupDTO buildBackupDto(@NonNull String basePath, @NonNull Path backupPath) {
        Assert.notNull(basePath, "Base path must not be null");
        Assert.notNull(backupPath, "Backup path must not be null");

        String backupFileName = backupPath.getFileName().toString();
        BackupDTO backup = new BackupDTO();
        try {
            backup.setDownloadLink(buildDownloadUrl(basePath, backupFileName));
            backup.setFilename(backupFileName);
            backup.setUpdateTime(Files.getLastModifiedTime(backupPath).toMillis());
            backup.setFileSize(Files.size(backupPath));
        } catch (IOException e) {
            throw new ServiceException("Failed to access file " + backupPath, e);
        }

        return backup;
    }

    /**
     * Builds download url.
     *
     * @param filename filename must not be blank
     * @return download url
     */
    @NonNull
    private String buildDownloadUrl(@NonNull String basePath, @NonNull String filename) {
        Assert.notNull(basePath, "Base path must not be null");
        Assert.hasText(filename, "File name must not be blank");

        // Composite http url
        String backupUri = basePath + HaloUtils.URL_SEPARATOR + filename;

        // Get a one-time token
        String oneTimeToken = oneTimeTokenService.create(backupUri);

        // Build full url
        return HaloUtils.compositeHttpUrl(optionService.getBlogBaseUrl(), backupUri)
            + "?"
            + HaloConst.ONE_TIME_TOKEN_QUERY_NAME
            + "=" + oneTimeToken;
    }

    /**
     * Builds a manifest of a JSON data backup by parsing the content in memory. No database write
     * nor work directory modification happens here.
     *
     * @param jsonContent raw json content of the data backup
     * @return data manifest
     */
    private BackupManifestDTO.DataManifest buildDataManifest(String jsonContent) {
        Map<String, Object> data;
        try {
            data = JsonUtils.DEFAULT_JSON_MAPPER
                .readValue(jsonContent, new TypeReference<HashMap<String, Object>>() {
                });
        } catch (IOException e) {
            throw new BadRequestException("非法的备份文件：无法解析 JSON 数据", e);
        }
        if (data == null) {
            throw new BadRequestException("非法的备份文件：JSON 数据为空");
        }

        BackupManifestDTO.DataManifest manifest = new BackupManifestDTO.DataManifest();
        manifest.setVersion(asString(data.get("version")));
        manifest.setExportDate(asString(data.get("export_date")));

        Map<String, Integer> counts = manifest.getContentCounts();
        for (String key : DATA_TABLE_KEYS) {
            counts.put(key, sizeOf(data.get(key)));
        }

        for (Map.Entry<String, Supplier<List<?>>> entry : conflictSources().entrySet()) {
            String table = entry.getKey();
            List<Map<String, Object>> rows = rowsOf(data.get(table));
            if (rows.isEmpty()) {
                continue;
            }
            Set<String> backupIds = idsFromRows(rows);
            Set<String> existingIds = idsFromEntities(entry.getValue().get());

            BackupManifestDTO.TableConflict conflict = new BackupManifestDTO.TableConflict(table);
            conflict.setBackupCount(rows.size());
            conflict.setExistingCount(existingIds.size());

            Set<String> conflictIds = new LinkedHashSet<>(backupIds);
            conflictIds.retainAll(existingIds);
            conflict.setConflictCount(conflictIds.size());
            conflict.setSampleConflictIds(conflictIds.stream()
                .limit(CONFLICT_SAMPLE_LIMIT)
                .collect(Collectors.toList()));
            manifest.getConflicts().add(conflict);
        }

        List<Map<String, Object>> optionRows = rowsOf(data.get("options"));
        if (!optionRows.isEmpty()) {
            Set<String> backupKeys = optionRows.stream()
                .map(row -> asString(row.get("key")))
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
            Set<String> existingKeys = optionService.listAll().stream()
                .map(Option::getKey)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
            backupKeys.retainAll(existingKeys);
            manifest.setConflictingOptionKeys(new ArrayList<>(backupKeys));
        }

        boolean backupHasUser = !rowsOf(data.get("user")).isEmpty();
        manifest.setUserConflict(backupHasUser && !userService.listAll().isEmpty());

        return manifest;
    }

    /**
     * Builds a manifest of an archive backup by streaming its entries. The archive is never
     * extracted and the work directory is never touched.
     *
     * @param inputStream archive input stream
     * @param type backup type
     * @return archive manifest
     */
    private BackupManifestDTO.ArchiveManifest buildArchiveManifest(InputStream inputStream,
        BackupType type) {
        BackupManifestDTO.ArchiveManifest manifest = new BackupManifestDTO.ArchiveManifest();
        Set<String> topLevelEntries = new LinkedHashSet<>();
        int markdownCount = 0;
        int uploadFileCount = 0;
        boolean containsUpload = false;

        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry = zipInputStream.getNextEntry();
            if (entry == null) {
                throw new BadRequestException("非法的备份文件：不是有效的 ZIP 压缩包或压缩包为空");
            }
            while (entry != null) {
                manifest.setTotalEntries(manifest.getTotalEntries() + 1);

                String name = normalizeEntryName(entry.getName());
                String topSegment = topSegment(name);
                if (StringUtils.isNotBlank(topSegment)) {
                    topLevelEntries.add(topSegment);
                }
                boolean uploadEntry = "upload".equalsIgnoreCase(topSegment);
                if (uploadEntry) {
                    containsUpload = true;
                }

                if (entry.isDirectory()) {
                    manifest.setDirectoryCount(manifest.getDirectoryCount() + 1);
                } else {
                    manifest.setFileCount(manifest.getFileCount() + 1);
                    if (StringUtils.endsWithIgnoreCase(name, ".md")) {
                        markdownCount++;
                    }
                    if (uploadEntry) {
                        uploadFileCount++;
                    }
                }

                zipInputStream.closeEntry();
                entry = zipInputStream.getNextEntry();
            }
        } catch (ZipException e) {
            throw new BadRequestException("非法的备份文件：不是有效的 ZIP 压缩包", e);
        } catch (IOException e) {
            throw new ServiceException("Failed to read backup archive", e);
        }

        manifest.setTopLevelEntries(new ArrayList<>(topLevelEntries));
        if (type == BackupType.MARKDOWN) {
            manifest.setMarkdownCount(markdownCount);
            manifest.setContainsUpload(containsUpload);
            manifest.setUploadFileCount(uploadFileCount);
        }
        return manifest;
    }

    /**
     * Maps core content table names to a supplier returning the records currently stored on the
     * site. Only read operations ({@code listAll}) are referenced, so calling the suppliers never
     * mutates any data.
     *
     * @return ordered map of table name to existing-record supplier
     */
    private Map<String, Supplier<List<?>>> conflictSources() {
        Map<String, Supplier<List<?>>> sources = new LinkedHashMap<>();
        sources.put("posts", postService::listAll);
        sources.put("sheets", sheetService::listAll);
        sources.put("post_comments", postCommentService::listAll);
        sources.put("sheet_comments", sheetCommentService::listAll);
        sources.put("journal_comments", journalCommentService::listAll);
        sources.put("attachments", attachmentService::listAll);
        sources.put("categories", categoryService::listAll);
        sources.put("tags", tagService::listAll);
        sources.put("menus", menuService::listAll);
        sources.put("links", linkService::listAll);
        sources.put("photos", photoService::listAll);
        sources.put("journals", journalService::listAll);
        sources.put("theme_settings", themeSettingService::listAll);
        sources.put("options", optionService::listAll);
        return sources;
    }

    private void populateWarnings(BackupManifestDTO manifest) {
        List<String> warnings = manifest.getWarnings();

        BackupManifestDTO.DataManifest data = manifest.getData();
        if (data != null) {
            int totalConflicts = data.getConflicts().stream()
                .mapToInt(BackupManifestDTO.TableConflict::getConflictCount)
                .sum();
            if (totalConflicts > 0) {
                warnings.add("检测到 " + totalConflicts
                    + " 处主键冲突，导入时这些记录可能与现有数据冲突。");
            }
            if (!data.getConflictingOptionKeys().isEmpty()) {
                warnings.add("检测到 " + data.getConflictingOptionKeys().size()
                    + " 个选项键已存在，导入会重复写入并可能覆盖站点关键配置。");
            }
            if (data.isUserConflict()) {
                warnings.add("当前站点已存在用户，导入备份中的用户可能失败或产生冲突。");
            }
            warnings.add("导入数据为追加写入，不会自动清空现有数据。");
        }

        BackupManifestDTO.ArchiveManifest archive = manifest.getArchive();
        if (archive != null && manifest.getBackupType() == BackupType.MARKDOWN) {
            if (Boolean.TRUE.equals(archive.getContainsUpload())) {
                int uploadFiles = Optional.ofNullable(archive.getUploadFileCount()).orElse(0);
                warnings.add("该 Markdown 备份包含 upload 附件目录（" + uploadFiles + " 个文件）。");
            } else {
                warnings.add("该 Markdown 备份不包含 upload 附件目录，导入后附件可能丢失。");
            }
        }
    }

    private static int sizeOf(Object value) {
        return value instanceof Collection ? ((Collection<?>) value).size() : 0;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rowsOf(Object value) {
        if (!(value instanceof Collection)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object item : (Collection<?>) value) {
            if (item instanceof Map) {
                rows.add((Map<String, Object>) item);
            }
        }
        return rows;
    }

    private static Set<String> idsFromRows(List<Map<String, Object>> rows) {
        Set<String> ids = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            String id = asString(row.get("id"));
            if (StringUtils.isNotBlank(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static Set<String> idsFromEntities(List<?> entities) {
        Set<String> ids = new LinkedHashSet<>();
        for (Object entity : entities) {
            String id = invokeGetId(entity);
            if (StringUtils.isNotBlank(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static String invokeGetId(Object entity) {
        if (entity == null) {
            return null;
        }
        try {
            Method method = entity.getClass().getMethod("getId");
            Object id = method.invoke(entity);
            return id == null ? null : String.valueOf(id);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String normalizeEntryName(String name) {
        if (name == null) {
            return "";
        }
        String normalized = name.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static String topSegment(String normalizedName) {
        if (StringUtils.isBlank(normalizedName)) {
            return "";
        }
        int slashIndex = normalizedName.indexOf('/');
        return slashIndex >= 0 ? normalizedName.substring(0, slashIndex) : normalizedName;
    }

}
