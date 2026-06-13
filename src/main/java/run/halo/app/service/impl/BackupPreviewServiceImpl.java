package run.halo.app.service.impl;

import static run.halo.app.utils.FileUtils.checkDirectoryTraversal;

import cn.hutool.core.io.IoUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.multipart.MultipartFile;
import run.halo.app.config.properties.HaloProperties;
import run.halo.app.exception.BadRequestException;
import run.halo.app.exception.NotFoundException;
import run.halo.app.exception.ServiceException;
import run.halo.app.model.dto.BackupPreviewDTO;
import run.halo.app.model.dto.DataImportConflictDTO;
import run.halo.app.model.dto.JsonDataPreviewDetail;
import run.halo.app.model.dto.MarkdownPreviewDetail;
import run.halo.app.model.dto.OptionChangePreview;
import run.halo.app.model.dto.WorkDirPreviewDetail;
import run.halo.app.model.entity.BasePost;
import run.halo.app.model.entity.Category;
import run.halo.app.model.entity.Tag;
import run.halo.app.service.AttachmentService;
import run.halo.app.service.BackupPreviewService;
import run.halo.app.service.BackupService.BackupType;
import run.halo.app.service.CategoryService;
import run.halo.app.service.CommentBlackListService;
import run.halo.app.service.JournalCommentService;
import run.halo.app.service.JournalService;
import run.halo.app.service.LinkService;
import run.halo.app.service.LogService;
import run.halo.app.service.MenuService;
import run.halo.app.service.OptionService;
import run.halo.app.service.PhotoService;
import run.halo.app.service.PostCommentService;
import run.halo.app.service.PostMetaService;
import run.halo.app.service.PostService;
import run.halo.app.service.SheetCommentService;
import run.halo.app.service.SheetMetaService;
import run.halo.app.service.SheetService;
import run.halo.app.service.TagService;
import run.halo.app.service.ThemeSettingService;
import run.halo.app.service.UserService;
import run.halo.app.service.base.CrudService;
import run.halo.app.utils.FileUtils;
import run.halo.app.utils.JsonUtils;

/**
 * Backup preview service implementation.
 * All methods are read-only and never write to the database or work directory.
 *
 * @author ryanwang
 */
@Service
@Slf4j
public class BackupPreviewServiceImpl implements BackupPreviewService {

    private static final int MAX_SAMPLE_SIZE = 10;

    /**
     * Sensitive option keys to monitor for import conflict preview.
     */
    private static final List<String> SENSITIVE_OPTION_KEYS = List.of(
        "blog_title", "blog_url", "blog_logo", "blog_favicon",
        "blog_footer_info", "blog_locale"
    );

    /**
     * Entity types that have a slug field.
     */
    private static final Set<String> SLUG_ENTITY_TYPES = Set.of(
        "posts", "sheets", "categories", "tags"
    );

    /**
     * Non-entity keys in JSON data export that should be skipped during
     * entity counting.
     */
    private static final Set<String> NON_ENTITY_KEYS = Set.of(
        "version", "export_date"
    );

    private final HaloProperties haloProperties;
    private final OptionService optionService;
    private final PostService postService;
    private final SheetService sheetService;
    private final CategoryService categoryService;
    private final TagService tagService;
    private final AttachmentService attachmentService;
    private final CommentBlackListService commentBlackListService;
    private final JournalService journalService;
    private final JournalCommentService journalCommentService;
    private final LinkService linkService;
    private final LogService logService;
    private final MenuService menuService;
    private final PhotoService photoService;
    private final PostCommentService postCommentService;
    private final PostMetaService postMetaService;
    private final SheetCommentService sheetCommentService;
    private final SheetMetaService sheetMetaService;
    private final ThemeSettingService themeSettingService;
    private final UserService userService;

    public BackupPreviewServiceImpl(HaloProperties haloProperties,
        OptionService optionService,
        PostService postService,
        SheetService sheetService,
        CategoryService categoryService,
        TagService tagService,
        AttachmentService attachmentService,
        CommentBlackListService commentBlackListService,
        JournalService journalService,
        JournalCommentService journalCommentService,
        LinkService linkService,
        LogService logService,
        MenuService menuService,
        PhotoService photoService,
        PostCommentService postCommentService,
        PostMetaService postMetaService,
        SheetCommentService sheetCommentService,
        SheetMetaService sheetMetaService,
        ThemeSettingService themeSettingService,
        UserService userService) {
        this.haloProperties = haloProperties;
        this.optionService = optionService;
        this.postService = postService;
        this.sheetService = sheetService;
        this.categoryService = categoryService;
        this.tagService = tagService;
        this.attachmentService = attachmentService;
        this.commentBlackListService = commentBlackListService;
        this.journalService = journalService;
        this.journalCommentService = journalCommentService;
        this.linkService = linkService;
        this.logService = logService;
        this.menuService = menuService;
        this.photoService = photoService;
        this.postCommentService = postCommentService;
        this.postMetaService = postMetaService;
        this.sheetCommentService = sheetCommentService;
        this.sheetMetaService = sheetMetaService;
        this.themeSettingService = themeSettingService;
        this.userService = userService;
    }

    @Override
    public BackupPreviewDTO previewExistingBackup(String filename, BackupType type) {
        Assert.hasText(filename, "File name must not be blank");
        Assert.notNull(type, "Backup type must not be null");

        String basePath = resolveBasePath(type);
        Path backupPath = Paths.get(basePath, filename).normalize();

        checkDirectoryTraversal(Paths.get(basePath), backupPath);

        if (!Files.exists(backupPath)) {
            throw new NotFoundException("The file " + filename + " was not found");
        }

        BackupPreviewDTO dto = new BackupPreviewDTO();
        dto.setBackupType(type.name());
        dto.setFilename(filename);

        try {
            dto.setFileSize(Files.size(backupPath));
            populateDetail(dto, backupPath, type);
        } catch (ZipException e) {
            throw new BadRequestException("Invalid or corrupted backup file", e);
        } catch (IOException e) {
            throw new ServiceException("Failed to preview backup file", e);
        }

        return dto;
    }

    @Override
    public BackupPreviewDTO previewUploadedBackup(MultipartFile file, BackupType type)
        throws IOException {
        Assert.notNull(file, "File must not be null");
        Assert.notNull(type, "Backup type must not be null");

        if (file.isEmpty()) {
            throw new BadRequestException("Uploaded file is empty");
        }

        Path tempDir = FileUtils.createTempDirectory();
        try {
            String originalFilename = file.getOriginalFilename();
            Path tempFile = tempDir.resolve(
                originalFilename != null ? originalFilename : "upload.tmp");
            Files.copy(file.getInputStream(), tempFile);

            BackupPreviewDTO dto = new BackupPreviewDTO();
            dto.setBackupType(type.name());
            dto.setFilename(null);
            dto.setFileSize(file.getSize());

            try {
                populateDetail(dto, tempFile, type);
            } catch (ZipException e) {
                throw new BadRequestException("Invalid or corrupted backup file", e);
            }

            return dto;
        } finally {
            FileUtils.deleteFolderQuietly(tempDir);
        }
    }

    @Override
    public DataImportConflictDTO previewDataImport(String filename) throws IOException {
        Assert.hasText(filename, "File name must not be blank");

        String basePath = haloProperties.getDataExportDir();
        Path jsonPath = Paths.get(basePath, filename).normalize();

        checkDirectoryTraversal(Paths.get(basePath), jsonPath);

        if (!Files.exists(jsonPath)) {
            throw new NotFoundException("The file " + filename + " was not found");
        }

        String jsonContent = new String(Files.readAllBytes(jsonPath), StandardCharsets.UTF_8);
        return buildConflictReport(jsonContent);
    }

    @Override
    public DataImportConflictDTO previewDataImportFromUpload(MultipartFile file)
        throws IOException {
        Assert.notNull(file, "File must not be null");

        if (file.isEmpty()) {
            throw new BadRequestException("Uploaded file is empty");
        }

        String jsonContent = IoUtil.read(file.getInputStream(), StandardCharsets.UTF_8);
        return buildConflictReport(jsonContent);
    }

    // ====== Private helpers ======

    private String resolveBasePath(BackupType type) {
        switch (type) {
            case WHOLE_SITE:
                return haloProperties.getBackupDir();
            case JSON_DATA:
                return haloProperties.getDataExportDir();
            case MARKDOWN:
                return haloProperties.getBackupMarkdownDir();
            default:
                throw new IllegalArgumentException("Unknown backup type: " + type);
        }
    }

    private void populateDetail(BackupPreviewDTO dto, Path filePath, BackupType type)
        throws IOException {
        switch (type) {
            case WHOLE_SITE:
                dto.setWorkDirDetail(previewWorkDirZip(filePath));
                break;
            case JSON_DATA:
                dto.setJsonDataDetail(previewJsonData(filePath));
                break;
            case MARKDOWN:
                dto.setMarkdownDetail(previewMarkdownZip(filePath));
                break;
            default:
                throw new IllegalArgumentException("Unknown backup type: " + type);
        }
    }

    private WorkDirPreviewDetail previewWorkDirZip(Path zipPath) throws IOException {
        Path tempDir = FileUtils.createTempDirectory();
        try {
            unzipSafely(zipPath, tempDir);

            WorkDirPreviewDetail detail = new WorkDirPreviewDetail();

            // Collect top-level entries
            List<String> topLevelEntries;
            try (Stream<Path> stream = Files.list(tempDir)) {
                topLevelEntries = stream
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .collect(Collectors.toList());
            }
            detail.setTopLevelEntries(topLevelEntries);

            // Count all regular files and compute total uncompressed size
            long[] stats = computeFileStats(tempDir);
            detail.setTotalFileCount((int) stats[0]);
            detail.setTotalUncompressedSize(stats[1]);

            // Check for known directories
            detail.setHasDatabase(Files.isDirectory(tempDir.resolve("db")));
            detail.setHasThemes(Files.isDirectory(tempDir.resolve("themes")));
            detail.setHasUploads(Files.isDirectory(tempDir.resolve("upload")));

            // Collect theme names
            if (Boolean.TRUE.equals(detail.getHasThemes())) {
                Path themesDir = tempDir.resolve("themes");
                try (Stream<Path> stream = Files.list(themesDir)) {
                    detail.setThemeNames(stream
                        .filter(Files::isDirectory)
                        .map(p -> p.getFileName().toString())
                        .sorted()
                        .collect(Collectors.toList()));
                }
            } else {
                detail.setThemeNames(Collections.emptyList());
            }

            return detail;
        } finally {
            FileUtils.deleteFolderQuietly(tempDir);
        }
    }

    private JsonDataPreviewDetail previewJsonData(Path jsonPath) throws IOException {
        String jsonContent;
        try {
            jsonContent = new String(Files.readAllBytes(jsonPath), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ServiceException("Failed to read JSON data file", e);
        }

        HashMap<String, Object> data = parseJsonData(jsonContent);

        JsonDataPreviewDetail detail = new JsonDataPreviewDetail();
        detail.setVersion(getStringValue(data, "version"));
        detail.setExportDate(getStringValue(data, "export_date"));

        Map<String, Integer> entityCounts = countEntities(data);
        detail.setEntityCounts(entityCounts);
        detail.setTotalEntityCount(
            entityCounts.values().stream().mapToInt(Integer::intValue).sum());

        return detail;
    }

    private MarkdownPreviewDetail previewMarkdownZip(Path zipPath) throws IOException {
        Path tempDir = FileUtils.createTempDirectory();
        try {
            unzipSafely(zipPath, tempDir);

            MarkdownPreviewDetail detail = new MarkdownPreviewDetail();

            // Find all .md files
            List<String> mdFiles;
            try (Stream<Path> stream = Files.walk(tempDir)) {
                mdFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .collect(Collectors.toList());
            }

            detail.setPostCount(mdFiles.size());
            detail.setSamplePostTitles(
                mdFiles.subList(0, Math.min(mdFiles.size(), MAX_SAMPLE_SIZE)));

            // Check for upload directory
            Path uploadDir = findUploadDir(tempDir);
            if (uploadDir != null && Files.isDirectory(uploadDir)) {
                detail.setHasUploadDir(true);
                try (Stream<Path> stream = Files.walk(uploadDir)) {
                    detail.setUploadFileCount((int) stream
                        .filter(Files::isRegularFile)
                        .count());
                }
            } else {
                detail.setHasUploadDir(false);
                detail.setUploadFileCount(0);
            }

            return detail;
        } finally {
            FileUtils.deleteFolderQuietly(tempDir);
        }
    }

    private DataImportConflictDTO buildConflictReport(String jsonContent) {
        HashMap<String, Object> data;
        try {
            data = parseJsonData(jsonContent);
        } catch (IOException e) {
            throw new BadRequestException("Invalid JSON data file", e);
        }

        DataImportConflictDTO dto = new DataImportConflictDTO();

        // Entity counts
        Map<String, Integer> entityCounts = countEntities(data);
        dto.setIncomingEntityCounts(entityCounts);

        // ID conflict detection
        Map<String, List<Object>> conflictingIds = new LinkedHashMap<>();
        Map<String, List<String>> conflictingSlugs = new LinkedHashMap<>();

        // Check ID conflicts for each entity type
        checkIdConflicts(data, "attachments", attachmentService, conflictingIds);
        checkIdConflicts(data, "categories", categoryService, conflictingIds);
        checkIdConflicts(data, "comment_black_list", commentBlackListService,
            conflictingIds);
        checkIdConflicts(data, "journals", journalService, conflictingIds);
        checkIdConflicts(data, "journal_comments", journalCommentService, conflictingIds);
        checkIdConflicts(data, "links", linkService, conflictingIds);
        checkIdConflicts(data, "logs", logService, conflictingIds);
        checkIdConflicts(data, "menus", menuService, conflictingIds);
        checkIdConflicts(data, "options", optionService, conflictingIds);
        checkIdConflicts(data, "photos", photoService, conflictingIds);
        checkIdConflicts(data, "posts", postService, conflictingIds);
        checkIdConflicts(data, "post_comments", postCommentService, conflictingIds);
        checkIdConflicts(data, "post_metas", postMetaService, conflictingIds);
        checkIdConflicts(data, "sheets", sheetService, conflictingIds);
        checkIdConflicts(data, "sheet_comments", sheetCommentService, conflictingIds);
        checkIdConflicts(data, "sheet_metas", sheetMetaService, conflictingIds);
        checkIdConflicts(data, "tags", tagService, conflictingIds);
        checkIdConflicts(data, "theme_settings", themeSettingService, conflictingIds);
        checkIdConflicts(data, "user", userService, conflictingIds);

        // Slug conflict detection for entity types that have slugs
        checkSlugConflicts(data, "posts", postService, conflictingSlugs);
        checkSlugConflicts(data, "sheets", sheetService, conflictingSlugs);
        checkSlugConflictsForCategory(data, conflictingSlugs);
        checkSlugConflictsForTag(data, conflictingSlugs);

        dto.setConflictingIds(conflictingIds);
        dto.setConflictingSlugs(conflictingSlugs);

        // Option change detection
        dto.setOptionChanges(detectOptionChanges(data));

        // Total conflicts
        int totalConflicts = conflictingIds.values().stream()
            .mapToInt(List::size).sum()
            + conflictingSlugs.values().stream().mapToInt(List::size).sum();
        dto.setTotalConflicts(totalConflicts);

        return dto;
    }

    private HashMap<String, Object> parseJsonData(String jsonContent) throws IOException {
        ObjectMapper mapper = JsonUtils.createDefaultJsonMapper();
        TypeReference<HashMap<String, Object>> typeRef = new TypeReference<>() {
        };
        return mapper.readValue(jsonContent, typeRef);
    }

    private Map<String, Integer> countEntities(HashMap<String, Object> data) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            if (NON_ENTITY_KEYS.contains(key)) {
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof List) {
                counts.put(key, ((List<?>) value).size());
            } else if (value != null) {
                counts.put(key, 1);
            } else {
                counts.put(key, 0);
            }
        }
        return counts;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <I> void checkIdConflicts(HashMap<String, Object> data,
        String entityType, CrudService<?, I> service,
        Map<String, List<Object>> conflictingIds) {
        Object entityData = data.get(entityType);
        if (!(entityData instanceof List)) {
            return;
        }
        List<?> entityList = (List<?>) entityData;
        if (entityList.isEmpty()) {
            return;
        }

        List<Object> incomingIds = extractFieldValues(entityList, "id");
        if (incomingIds.isEmpty()) {
            return;
        }

        try {
            // Determine the ID type from the service's generic parameter
            // by probing the first existing entity or using the service type
            boolean expectsLong = isLongIdService(service);

            List<I> typedIds = new ArrayList<>();
            for (Object id : incomingIds) {
                if (expectsLong && id instanceof Integer) {
                    typedIds.add((I) Long.valueOf(((Integer) id).longValue()));
                } else if (!expectsLong && id instanceof Long) {
                    typedIds.add((I) Integer.valueOf(((Long) id).intValue()));
                } else {
                    typedIds.add((I) id);
                }
            }
            List<?> existing = service.listAllByIds(typedIds);
            if (!existing.isEmpty()) {
                Set<Object> existingIdSet = existing.stream()
                    .map(e -> {
                        try {
                            java.lang.reflect.Method getId =
                                e.getClass().getMethod("getId");
                            return getId.invoke(e);
                        } catch (Exception ex) {
                            return null;
                        }
                    })
                    .collect(Collectors.toSet());

                List<Object> conflicts = incomingIds.stream()
                    .filter(id -> {
                        // Normalize incoming ID to match existing ID type
                        Object normalized = expectsLong && id instanceof Integer
                            ? Long.valueOf(((Integer) id).longValue()) : id;
                        return existingIdSet.contains(normalized);
                    })
                    .collect(Collectors.toList());

                if (!conflicts.isEmpty()) {
                    conflictingIds.put(entityType, conflicts);
                }
            }
        } catch (ClassCastException e) {
            log.debug("Skipping ID conflict check for {} due to type mismatch", entityType);
        }
    }

    private <I> boolean isLongIdService(CrudService<?, I> service) {
        // Check if the service deals with Long IDs by examining the interface
        for (java.lang.reflect.Type iface : service.getClass().getGenericInterfaces()) {
            if (iface instanceof java.lang.reflect.ParameterizedType) {
                java.lang.reflect.ParameterizedType pt =
                    (java.lang.reflect.ParameterizedType) iface;
                java.lang.reflect.Type[] typeArgs = pt.getActualTypeArguments();
                if (typeArgs.length >= 2 && typeArgs[1] == Long.class) {
                    return true;
                }
            }
        }
        // Also check class hierarchy
        Class<?> clazz = service.getClass();
        while (clazz != null) {
            for (java.lang.reflect.Type iface : clazz.getGenericInterfaces()) {
                if (iface instanceof java.lang.reflect.ParameterizedType) {
                    java.lang.reflect.ParameterizedType pt =
                        (java.lang.reflect.ParameterizedType) iface;
                    java.lang.reflect.Type[] typeArgs = pt.getActualTypeArguments();
                    if (typeArgs.length >= 2 && typeArgs[1] == Long.class) {
                        return true;
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
        return false;
    }

    private void checkSlugConflicts(HashMap<String, Object> data,
        String entityType,
        run.halo.app.service.base.BasePostService<?> service,
        Map<String, List<String>> conflictingSlugs) {
        Object entityData = data.get(entityType);
        if (!(entityData instanceof List)) {
            return;
        }
        List<?> entityList = (List<?>) entityData;
        if (entityList.isEmpty()) {
            return;
        }

        List<String> incomingSlugs = extractStringFieldValues(entityList, "slug");
        if (incomingSlugs.isEmpty()) {
            return;
        }

        List<? extends BasePost> allPosts = service.listAll();
        Set<String> existingSlugs = allPosts.stream()
            .map(BasePost::getSlug)
            .collect(Collectors.toSet());

        List<String> conflicts = incomingSlugs.stream()
            .filter(existingSlugs::contains)
            .collect(Collectors.toList());

        if (!conflicts.isEmpty()) {
            conflictingSlugs.put(entityType, conflicts);
        }
    }

    private void checkSlugConflictsForCategory(HashMap<String, Object> data,
        Map<String, List<String>> conflictingSlugs) {
        Object entityData = data.get("categories");
        if (!(entityData instanceof List)) {
            return;
        }
        List<?> entityList = (List<?>) entityData;
        if (entityList.isEmpty()) {
            return;
        }

        List<String> incomingSlugs = extractStringFieldValues(entityList, "slug");
        if (incomingSlugs.isEmpty()) {
            return;
        }

        List<Category> allCategories = categoryService.listAll();
        Set<String> existingSlugs = allCategories.stream()
            .map(Category::getSlug)
            .collect(Collectors.toSet());

        List<String> conflicts = incomingSlugs.stream()
            .filter(existingSlugs::contains)
            .collect(Collectors.toList());

        if (!conflicts.isEmpty()) {
            conflictingSlugs.put("categories", conflicts);
        }
    }

    private void checkSlugConflictsForTag(HashMap<String, Object> data,
        Map<String, List<String>> conflictingSlugs) {
        Object entityData = data.get("tags");
        if (!(entityData instanceof List)) {
            return;
        }
        List<?> entityList = (List<?>) entityData;
        if (entityList.isEmpty()) {
            return;
        }

        List<String> incomingSlugs = extractStringFieldValues(entityList, "slug");
        if (incomingSlugs.isEmpty()) {
            return;
        }

        List<Tag> allTags = tagService.listAll();
        Set<String> existingSlugs = allTags.stream()
            .map(Tag::getSlug)
            .collect(Collectors.toSet());

        List<String> conflicts = incomingSlugs.stream()
            .filter(existingSlugs::contains)
            .collect(Collectors.toList());

        if (!conflicts.isEmpty()) {
            conflictingSlugs.put("tags", conflicts);
        }
    }

    private List<OptionChangePreview> detectOptionChanges(
        HashMap<String, Object> data) {
        Object optionsData = data.get("options");
        if (!(optionsData instanceof List)) {
            return Collections.emptyList();
        }
        List<?> optionList = (List<?>) optionsData;
        if (optionList.isEmpty()) {
            return Collections.emptyList();
        }

        // Build a map of incoming option key -> value
        Map<String, String> incomingOptions = new LinkedHashMap<>();
        for (Object item : optionList) {
            if (item instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> optionMap = (Map<String, Object>) item;
                Object keyObj = optionMap.get("key");
                Object valueObj = optionMap.get("value");
                if (keyObj != null) {
                    incomingOptions.put(keyObj.toString(),
                        valueObj != null ? valueObj.toString() : null);
                }
            }
        }

        List<OptionChangePreview> changes = new ArrayList<>();
        for (String sensitiveKey : SENSITIVE_OPTION_KEYS) {
            if (!incomingOptions.containsKey(sensitiveKey)) {
                continue;
            }
            String incomingValue = incomingOptions.get(sensitiveKey);
            String currentValue = optionService.getByKey(sensitiveKey)
                .map(Object::toString)
                .orElse(null);

            if (!java.util.Objects.equals(currentValue, incomingValue)) {
                changes.add(new OptionChangePreview(sensitiveKey, currentValue, incomingValue));
            }
        }

        return changes;
    }

    private List<Object> extractFieldValues(List<?> entityList,
        String fieldName) {
        List<Object> values = new ArrayList<>();
        for (Object item : entityList) {
            if (item instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) item;
                Object value = map.get(fieldName);
                if (value != null) {
                    values.add(value);
                }
            }
        }
        return values;
    }

    private List<String> extractStringFieldValues(List<?> entityList,
        String fieldName) {
        List<String> values = new ArrayList<>();
        for (Object item : entityList) {
            if (item instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) item;
                Object value = map.get(fieldName);
                if (value != null) {
                    values.add(value.toString());
                }
            }
        }
        return values;
    }

    private String getStringValue(HashMap<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }

    private void unzipSafely(Path zipPath, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
            FileUtils.unzip(zis, targetDir);
        }
    }

    /**
     * Computes file count and total uncompressed size.
     *
     * @return long array: [0] = file count, [1] = total size in bytes
     */
    private long[] computeFileStats(Path dir) throws IOException {
        long[] stats = new long[2];
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.filter(Files::isRegularFile).forEach(p -> {
                stats[0]++;
                try {
                    stats[1] += Files.size(p);
                } catch (IOException e) {
                    // ignore
                }
            });
        }
        return stats;
    }

    private Path findUploadDir(Path dir) throws IOException {
        // Look for "upload" directory at any level (typically at root)
        Path directUpload = dir.resolve("upload");
        if (Files.isDirectory(directUpload)) {
            return directUpload;
        }
        // Also check for upload/ inside subdirectories
        try (Stream<Path> stream = Files.walk(dir, 2)) {
            return stream
                .filter(Files::isDirectory)
                .filter(p -> p.getFileName().toString().equals("upload"))
                .findFirst()
                .orElse(null);
        }
    }
}
