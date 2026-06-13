package run.halo.app.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import run.halo.app.model.dto.UrlReplaceFieldDetail;
import run.halo.app.model.dto.UrlReplaceModuleDetail;
import run.halo.app.model.dto.UrlReplaceResult;
import run.halo.app.model.entity.Attachment;
import run.halo.app.model.entity.BaseComment;
import run.halo.app.model.entity.BasePost;
import run.halo.app.model.entity.Option;
import run.halo.app.model.entity.Photo;
import run.halo.app.model.entity.ThemeSetting;
import run.halo.app.model.enums.ReplaceableModule;
import run.halo.app.service.AttachmentService;
import run.halo.app.service.JournalCommentService;
import run.halo.app.service.OptionService;
import run.halo.app.service.PhotoService;
import run.halo.app.service.PostCommentService;
import run.halo.app.service.PostService;
import run.halo.app.service.SheetCommentService;
import run.halo.app.service.SheetService;
import run.halo.app.service.ThemeSettingService;
import run.halo.app.service.UrlReplaceScanService;

/**
 * Implementation of URL replacement scan and execution service.
 *
 * @author halo-dev
 */
@Slf4j
@Service
public class UrlReplaceScanServiceImpl implements UrlReplaceScanService {

    private final PostService postService;
    private final SheetService sheetService;
    private final PostCommentService postCommentService;
    private final SheetCommentService sheetCommentService;
    private final JournalCommentService journalCommentService;
    private final AttachmentService attachmentService;
    private final OptionService optionService;
    private final PhotoService photoService;
    private final ThemeSettingService themeSettingService;

    public UrlReplaceScanServiceImpl(PostService postService,
        SheetService sheetService,
        PostCommentService postCommentService,
        SheetCommentService sheetCommentService,
        JournalCommentService journalCommentService,
        AttachmentService attachmentService,
        OptionService optionService,
        PhotoService photoService,
        ThemeSettingService themeSettingService) {
        this.postService = postService;
        this.sheetService = sheetService;
        this.postCommentService = postCommentService;
        this.sheetCommentService = sheetCommentService;
        this.journalCommentService = journalCommentService;
        this.attachmentService = attachmentService;
        this.optionService = optionService;
        this.photoService = photoService;
        this.themeSettingService = themeSettingService;
    }

    @Override
    public UrlReplaceResult scan(@NonNull String oldUrl, @NonNull String newUrl,
        @NonNull Set<ReplaceableModule> modules) {

        UrlReplaceResult result = new UrlReplaceResult(true, oldUrl, newUrl);

        for (ReplaceableModule module : modules) {
            UrlReplaceModuleDetail detail = scanModule(module, oldUrl, newUrl);
            result.getModules().add(detail);
        }

        result.computeTotal();
        return result;
    }

    @Override
    public UrlReplaceResult execute(@NonNull String oldUrl, @NonNull String newUrl,
        @NonNull Set<ReplaceableModule> modules) {

        // Scan first to get pre-execution statistics
        UrlReplaceResult scanResult = scan(oldUrl, newUrl, modules);

        // Execute replacement on each selected module
        for (ReplaceableModule module : modules) {
            executeModule(module, oldUrl, newUrl);
        }

        // Mark as non-dry-run and return the pre-execution stats
        scanResult.setDryRun(false);
        return scanResult;
    }

    /**
     * Scan a single module for oldUrl occurrences.
     */
    private UrlReplaceModuleDetail scanModule(ReplaceableModule module,
        String oldUrl, String newUrl) {

        switch (module) {
            case POSTS:
                return scanBasePostModule(module, postService.listAll(), oldUrl, newUrl);
            case SHEETS:
                return scanBasePostModule(module, sheetService.listAll(), oldUrl, newUrl);
            case POST_COMMENTS:
                return scanBaseCommentModule(module, postCommentService.listAll(), oldUrl,
                    newUrl);
            case SHEET_COMMENTS:
                return scanBaseCommentModule(module, sheetCommentService.listAll(), oldUrl,
                    newUrl);
            case JOURNAL_COMMENTS:
                return scanBaseCommentModule(module, journalCommentService.listAll(), oldUrl,
                    newUrl);
            case ATTACHMENTS:
                return scanGenericModule(module, attachmentService.listAll(), oldUrl, newUrl,
                    attachmentFieldAccessors());
            case OPTIONS:
                return scanGenericModule(module, optionService.listAll(), oldUrl, newUrl,
                    optionFieldAccessors());
            case PHOTOS:
                return scanGenericModule(module, photoService.listAll(), oldUrl, newUrl,
                    photoFieldAccessors());
            case THEME_SETTINGS:
                return scanGenericModule(module, themeSettingService.listAll(), oldUrl, newUrl,
                    themeSettingFieldAccessors());
            default:
                log.warn("Unknown module: {}", module);
                return new UrlReplaceModuleDetail(module);
        }
    }

    /**
     * Execute URL replacement on a single module using the existing service methods.
     */
    private void executeModule(ReplaceableModule module, String oldUrl, String newUrl) {
        switch (module) {
            case POSTS:
                postService.replaceUrl(oldUrl, newUrl);
                break;
            case SHEETS:
                sheetService.replaceUrl(oldUrl, newUrl);
                break;
            case POST_COMMENTS:
                postCommentService.replaceUrl(oldUrl, newUrl);
                break;
            case SHEET_COMMENTS:
                sheetCommentService.replaceUrl(oldUrl, newUrl);
                break;
            case JOURNAL_COMMENTS:
                journalCommentService.replaceUrl(oldUrl, newUrl);
                break;
            case ATTACHMENTS:
                attachmentService.replaceUrl(oldUrl, newUrl);
                break;
            case OPTIONS:
                optionService.replaceUrl(oldUrl, newUrl);
                break;
            case PHOTOS:
                photoService.replaceUrl(oldUrl, newUrl);
                break;
            case THEME_SETTINGS:
                themeSettingService.replaceUrl(oldUrl, newUrl);
                break;
            default:
                log.warn("Unknown module: {}", module);
                break;
        }
    }

    /**
     * Scan entities extending BasePost (Post, Sheet).
     */
    private <T extends BasePost> UrlReplaceModuleDetail scanBasePostModule(
        ReplaceableModule module, List<T> entities, String oldUrl, String newUrl) {

        Map<String, Function<T, String>> accessors = new LinkedHashMap<>();
        accessors.put("thumbnail", BasePost::getThumbnail);
        accessors.put("originalContent", BasePost::getOriginalContent);
        accessors.put("formatContent", BasePost::getFormatContent);

        return scanGenericModule(module, entities, oldUrl, newUrl, accessors);
    }

    /**
     * Scan entities extending BaseComment (PostComment, SheetComment, JournalComment).
     */
    private <T extends BaseComment> UrlReplaceModuleDetail scanBaseCommentModule(
        ReplaceableModule module, List<T> entities, String oldUrl, String newUrl) {

        Map<String, Function<T, String>> accessors = new LinkedHashMap<>();
        accessors.put("authorUrl", BaseComment::getAuthorUrl);

        return scanGenericModule(module, entities, oldUrl, newUrl, accessors);
    }

    /**
     * Generic scan method that counts oldUrl occurrences in entity fields.
     * Does NOT modify any data — purely read-only.
     */
    private <T> UrlReplaceModuleDetail scanGenericModule(ReplaceableModule module,
        List<T> entities, String oldUrl, String newUrl,
        Map<String, Function<T, String>> fieldAccessors) {

        UrlReplaceModuleDetail detail = new UrlReplaceModuleDetail(module);
        detail.setTotalEntities(entities.size());

        int matchedEntities = 0;
        List<UrlReplaceFieldDetail> fieldDetails = new ArrayList<>();

        // Initialize per-field counters
        Map<String, Integer> fieldMatchedCounts = new LinkedHashMap<>();
        Map<String, Integer> fieldTotalOccurrences = new LinkedHashMap<>();
        for (String fieldName : fieldAccessors.keySet()) {
            fieldMatchedCounts.put(fieldName, 0);
            fieldTotalOccurrences.put(fieldName, 0);
        }

        // Scan each entity
        for (T entity : entities) {
            boolean entityMatched = false;

            for (Map.Entry<String, Function<T, String>> entry : fieldAccessors.entrySet()) {
                String fieldName = entry.getKey();
                String fieldValue = entry.getValue().apply(entity);

                if (StringUtils.isNotEmpty(fieldValue)) {
                    int occurrences = countOccurrences(fieldValue, oldUrl);
                    if (occurrences > 0) {
                        entityMatched = true;
                        fieldMatchedCounts.merge(fieldName, 1, Integer::sum);
                        fieldTotalOccurrences.merge(fieldName, occurrences, Integer::sum);
                    }

                    // Warning: newUrl already present in this field
                    if (fieldValue.contains(newUrl) && !oldUrl.equals(newUrl)) {
                        detail.getWarnings().add(
                            String.format("字段 '%s' 中已存在新 URL '%s'，可能产生重复",
                                fieldName, newUrl));
                    }
                }
            }

            if (entityMatched) {
                matchedEntities++;
            }
        }

        // Build field details
        for (String fieldName : fieldAccessors.keySet()) {
            fieldDetails.add(new UrlReplaceFieldDetail(
                fieldName,
                fieldMatchedCounts.get(fieldName),
                fieldTotalOccurrences.get(fieldName)));
        }

        detail.setMatchedEntities(matchedEntities);
        detail.setFields(fieldDetails);

        // Deduplicate warnings
        List<String> warnings = new ArrayList<>(
            new LinkedHashSet<>(detail.getWarnings()));
        detail.setWarnings(warnings);

        return detail;
    }

    /**
     * Count occurrences of a search string in text using literal matching.
     *
     * @param text the text to search in
     * @param search the string to search for
     * @return number of occurrences
     */
    static int countOccurrences(@NonNull String text, @NonNull String search) {
        if (search.isEmpty()) {
            return 0;
        }
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(search, idx)) != -1) {
            count++;
            idx += search.length();
        }
        return count;
    }

    // --- Field accessor definitions ---

    private Map<String, Function<Attachment, String>> attachmentFieldAccessors() {
        Map<String, Function<Attachment, String>> accessors = new LinkedHashMap<>();
        accessors.put("path", Attachment::getPath);
        accessors.put("thumbPath", Attachment::getThumbPath);
        return accessors;
    }

    private Map<String, Function<Option, String>> optionFieldAccessors() {
        Map<String, Function<Option, String>> accessors = new LinkedHashMap<>();
        accessors.put("value", Option::getValue);
        return accessors;
    }

    private Map<String, Function<Photo, String>> photoFieldAccessors() {
        Map<String, Function<Photo, String>> accessors = new LinkedHashMap<>();
        accessors.put("thumbnail", Photo::getThumbnail);
        accessors.put("url", Photo::getUrl);
        return accessors;
    }

    private Map<String, Function<ThemeSetting, String>> themeSettingFieldAccessors() {
        Map<String, Function<ThemeSetting, String>> accessors = new LinkedHashMap<>();
        accessors.put("value", ThemeSetting::getValue);
        return accessors;
    }
}
