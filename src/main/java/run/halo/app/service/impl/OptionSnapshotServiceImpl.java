package run.halo.app.service.impl;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import run.halo.app.config.properties.HaloProperties;
import run.halo.app.exception.BadRequestException;
import run.halo.app.exception.NotFoundException;
import run.halo.app.model.dto.OptionDiffDTO;
import run.halo.app.model.dto.SnapshotDTO;
import run.halo.app.model.dto.SnapshotData;
import run.halo.app.model.entity.Option;
import run.halo.app.service.OptionService;
import run.halo.app.service.OptionSnapshotService;
import run.halo.app.utils.JsonUtils;

/**
 * Option snapshot service implementation.
 *
 * @author halo
 * @date 2024-01-01
 */
@Slf4j
@Service
public class OptionSnapshotServiceImpl implements OptionSnapshotService {

    private static final Pattern NAME_PATTERN = Pattern.compile("[a-zA-Z0-9_-]+");

    private final OptionService optionService;

    private final Path snapshotDir;

    public OptionSnapshotServiceImpl(OptionService optionService,
        HaloProperties haloProperties) {
        this.optionService = optionService;
        this.snapshotDir =
            Paths.get(haloProperties.getWorkDir(), ".snapshots");
    }

    @Override
    @NonNull
    public SnapshotDTO create(@NonNull String name) {
        Assert.hasText(name, "Snapshot name must not be blank");
        validateName(name);

        Path file = resolveSnapshotFile(name);
        if (Files.exists(file)) {
            throw new BadRequestException(
                "Snapshot with name [" + name + "] already exists");
        }

        // Read all current options from DB as raw string values
        List<Option> options = optionService.listAll();
        Map<String, String> optionMap = new LinkedHashMap<>();
        for (Option option : options) {
            optionMap.put(option.getKey(), option.getValue());
        }

        SnapshotData data = new SnapshotData(name, new java.util.Date(), optionMap);

        try {
            ensureSnapshotDir();
            String json = JsonUtils.objectToJson(data);
            Files.write(file, json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(
                "Failed to create snapshot [" + name + "]", e);
        }

        log.info("Created option snapshot [{}] with [{}] options", name, optionMap.size());
        return toSnapshotDTO(data);
    }

    @Override
    @NonNull
    public List<SnapshotDTO> list() {
        ensureSnapshotDir();
        List<SnapshotDTO> result = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(snapshotDir, "*.json")) {
            for (Path file : stream) {
                try {
                    SnapshotData data = readSnapshotData(file);
                    result.add(toSnapshotDTO(data));
                } catch (IOException e) {
                    log.warn("Failed to read snapshot file: {}", file, e);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to list snapshots", e);
        }

        return result;
    }

    @Override
    @NonNull
    public SnapshotDTO get(@NonNull String name) {
        Assert.hasText(name, "Snapshot name must not be blank");
        validateName(name);

        SnapshotData data = loadSnapshot(name);
        return toSnapshotDTO(data);
    }

    @Override
    public void remove(@NonNull String name) {
        Assert.hasText(name, "Snapshot name must not be blank");
        validateName(name);

        Path file = resolveSnapshotFile(name);
        if (!Files.exists(file)) {
            throw new NotFoundException(
                "Snapshot with name [" + name + "] was not found");
        }

        try {
            Files.delete(file);
            log.info("Removed option snapshot [{}]", name);
        } catch (IOException e) {
            throw new RuntimeException(
                "Failed to remove snapshot [" + name + "]", e);
        }
    }

    @Override
    @NonNull
    public OptionDiffDTO diff(@NonNull String name) {
        Assert.hasText(name, "Snapshot name must not be blank");
        validateName(name);

        SnapshotData data = loadSnapshot(name);
        Map<String, String> snapshotOptions = data.getOptions();
        if (snapshotOptions == null) {
            snapshotOptions = Collections.emptyMap();
        }

        // Get current DB options as raw string values
        Map<String, String> currentOptions = getCurrentRawOptions();

        Map<String, String> added = new LinkedHashMap<>();
        Map<String, String> removed = new LinkedHashMap<>();
        Map<String, OptionDiffDTO.DiffEntry> changed = new LinkedHashMap<>();

        // Keys in snapshot but not in current → added (when applying, these would be created)
        for (Map.Entry<String, String> entry : snapshotOptions.entrySet()) {
            String key = entry.getKey();
            if (!currentOptions.containsKey(key)) {
                added.put(key, entry.getValue());
            }
        }

        // Keys in current but not in snapshot → removed (when applying, these would be deleted)
        for (Map.Entry<String, String> entry : currentOptions.entrySet()) {
            String key = entry.getKey();
            if (!snapshotOptions.containsKey(key)) {
                removed.put(key, entry.getValue());
            }
        }

        // Keys in both but with different values
        for (Map.Entry<String, String> entry : snapshotOptions.entrySet()) {
            String key = entry.getKey();
            String snapshotValue = entry.getValue();
            if (currentOptions.containsKey(key)) {
                String currentValue = currentOptions.get(key);
                if (!java.util.Objects.equals(snapshotValue, currentValue)) {
                    changed.put(key,
                        new OptionDiffDTO.DiffEntry(snapshotValue, currentValue));
                }
            }
        }

        OptionDiffDTO diffDTO = new OptionDiffDTO();
        diffDTO.setSnapshotName(name);
        diffDTO.setAdded(added);
        diffDTO.setRemoved(removed);
        diffDTO.setChanged(changed);
        return diffDTO;
    }

    @Override
    @Transactional
    public void apply(@NonNull String name) {
        Assert.hasText(name, "Snapshot name must not be blank");
        validateName(name);

        SnapshotData data = loadSnapshot(name);
        Map<String, String> snapshotOptions = data.getOptions();
        if (snapshotOptions == null) {
            snapshotOptions = Collections.emptyMap();
        }

        // Full rollback: remove all current options, then save snapshot options
        optionService.removeAll();

        // Convert to Map<String, Object> for save()
        Map<String, Object> optionMap = new HashMap<>(snapshotOptions);

        optionService.save(optionMap);
        log.info("Applied full snapshot [{}] (restored {} options)", name, snapshotOptions.size());
    }

    @Override
    @Transactional
    public void applyPartially(@NonNull String name, @NonNull List<String> keys) {
        Assert.hasText(name, "Snapshot name must not be blank");
        Assert.notEmpty(keys, "Keys must not be empty");
        validateName(name);

        SnapshotData data = loadSnapshot(name);
        Map<String, String> snapshotOptions = data.getOptions();
        if (snapshotOptions == null) {
            snapshotOptions = Collections.emptyMap();
        }

        // Filter snapshot to only requested keys
        Map<String, Object> filteredMap = new HashMap<>();
        for (String key : keys) {
            if (snapshotOptions.containsKey(key)) {
                filteredMap.put(key, snapshotOptions.get(key));
            }
        }

        if (CollectionUtils.isEmpty(filteredMap)) {
            log.warn("No matching keys found in snapshot [{}] for partial apply", name);
            return;
        }

        optionService.save(filteredMap);
        log.info("Applied partial snapshot [{}] (restored {} of {} requested keys)",
            name, filteredMap.size(), keys.size());
    }

    /**
     * Loads and returns the raw snapshot data from file.
     */
    @NonNull
    SnapshotData loadSnapshot(@NonNull String name) {
        Path file = resolveSnapshotFile(name);
        if (!Files.exists(file)) {
            throw new NotFoundException(
                "Snapshot with name [" + name + "] was not found");
        }

        try {
            return readSnapshotData(file);
        } catch (IOException e) {
            throw new RuntimeException(
                "Failed to load snapshot [" + name + "]", e);
        }
    }

    private SnapshotData readSnapshotData(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        String json = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        return JsonUtils.jsonToObject(json, SnapshotData.class);
    }

    private Map<String, String> getCurrentRawOptions() {
        List<Option> options = optionService.listAll();
        Map<String, String> result = new LinkedHashMap<>();
        for (Option option : options) {
            result.put(option.getKey(), option.getValue());
        }
        return result;
    }

    private SnapshotDTO toSnapshotDTO(SnapshotData data) {
        SnapshotDTO dto = new SnapshotDTO();
        dto.setName(data.getName());
        dto.setCreateTime(data.getCreateTime());
        dto.setOptionCount(data.getOptions() != null ? data.getOptions().size() : 0);
        return dto;
    }

    private void validateName(String name) {
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new BadRequestException(
                "Snapshot name [" + name + "] is invalid. "
                    + "Only letters, digits, underscores and hyphens are allowed.");
        }
    }

    private Path resolveSnapshotFile(String name) {
        return snapshotDir.resolve(name + ".json");
    }

    private void ensureSnapshotDir() {
        try {
            Files.createDirectories(snapshotDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create snapshot directory", e);
        }
    }
}
