package run.halo.app.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import run.halo.app.exception.AlreadyExistsException;
import run.halo.app.exception.BadRequestException;
import run.halo.app.exception.ServiceException;
import run.halo.app.model.dto.OptionSnapshotDTO;
import run.halo.app.model.entity.Option;
import run.halo.app.model.entity.OptionSnapshot;
import run.halo.app.model.enums.OptionDiffType;
import run.halo.app.model.params.OptionSnapshotParam;
import run.halo.app.model.vo.OptionSnapshotDetailVO;
import run.halo.app.model.vo.OptionSnapshotDiffVO;
import run.halo.app.repository.OptionSnapshotRepository;
import run.halo.app.service.OptionService;
import run.halo.app.service.OptionSnapshotService;
import run.halo.app.service.base.AbstractCrudService;
import run.halo.app.utils.JsonUtils;

/**
 * Option snapshot service implementation.
 *
 * @author halo
 */
@Slf4j
@Service
public class OptionSnapshotServiceImpl extends AbstractCrudService<OptionSnapshot, Integer>
    implements OptionSnapshotService {

    private final OptionSnapshotRepository optionSnapshotRepository;

    private final OptionService optionService;

    public OptionSnapshotServiceImpl(OptionSnapshotRepository optionSnapshotRepository,
        OptionService optionService) {
        super(optionSnapshotRepository);
        this.optionSnapshotRepository = optionSnapshotRepository;
        this.optionService = optionService;
    }

    @Override
    @Transactional
    public OptionSnapshotDTO createSnapshot(OptionSnapshotParam param) {
        Assert.notNull(param, "Option snapshot param must not be null");

        if (optionSnapshotRepository.existsByName(param.getName())) {
            throw new AlreadyExistsException(
                "Snapshot with name " + param.getName() + " already exists");
        }

        OptionSnapshot snapshot = new OptionSnapshot();
        snapshot.setName(param.getName());
        snapshot.setDescription(param.getDescription());
        snapshot.setData(writeData(currentRawOptions()));

        return convertTo(create(snapshot));
    }

    @Override
    public List<OptionSnapshotDTO> listDtos() {
        return listAll(Sort.by(Sort.Direction.DESC, "updateTime"))
            .stream()
            .map(this::convertTo)
            .collect(Collectors.toList());
    }

    @Override
    public OptionSnapshotDetailVO getDetailVoById(Integer id) {
        OptionSnapshot snapshot = getById(id);

        OptionSnapshotDetailVO detailVo = new OptionSnapshotDetailVO();
        detailVo.setId(snapshot.getId());
        detailVo.setName(snapshot.getName());
        detailVo.setDescription(snapshot.getDescription());
        detailVo.setCreateTime(snapshot.getCreateTime());
        detailVo.setUpdateTime(snapshot.getUpdateTime());
        detailVo.setData(parseData(snapshot.getData()));
        return detailVo;
    }

    @Override
    public OptionSnapshotDiffVO diff(Integer id) {
        OptionSnapshot snapshot = getById(id);

        Map<String, String> snapshotOptions = parseData(snapshot.getData());
        Map<String, String> currentOptions = currentRawOptions();

        // Union of keys, snapshot keys first to keep a stable, intuitive order
        Set<String> keys = new LinkedHashSet<>(snapshotOptions.keySet());
        keys.addAll(currentOptions.keySet());

        List<OptionSnapshotDiffVO.DiffItem> items = new LinkedList<>();
        int added = 0;
        int deleted = 0;
        int modified = 0;
        int unchanged = 0;

        for (String key : keys) {
            boolean inSnapshot = snapshotOptions.containsKey(key);
            boolean inCurrent = currentOptions.containsKey(key);
            String snapshotValue = snapshotOptions.get(key);
            String currentValue = currentOptions.get(key);

            OptionDiffType type;
            if (inSnapshot && !inCurrent) {
                type = OptionDiffType.DELETED;
                deleted++;
            } else if (!inSnapshot && inCurrent) {
                type = OptionDiffType.ADDED;
                added++;
            } else if (!Objects.equals(snapshotValue, currentValue)) {
                type = OptionDiffType.MODIFIED;
                modified++;
            } else {
                type = OptionDiffType.UNCHANGED;
                unchanged++;
            }

            items.add(
                new OptionSnapshotDiffVO.DiffItem(key, snapshotValue, currentValue, type));
        }

        return new OptionSnapshotDiffVO(snapshot.getId(), snapshot.getName(),
            added, deleted, modified, unchanged, items);
    }

    @Override
    @Transactional
    public void apply(Integer id) {
        OptionSnapshot snapshot = getById(id);

        Map<String, String> snapshotOptions = parseData(snapshot.getData());
        if (snapshotOptions.isEmpty()) {
            return;
        }

        // Route through OptionService#save so that defaults, dynamic conversion, cache eviction
        // and the OptionUpdatedEvent chain behave exactly like the existing option entry points.
        optionService.save(new LinkedHashMap<String, Object>(snapshotOptions));
    }

    @Override
    @Transactional
    public void applyKeys(Integer id, List<String> keys) {
        if (CollectionUtils.isEmpty(keys)) {
            throw new BadRequestException("Keys to restore must not be empty");
        }

        OptionSnapshot snapshot = getById(id);
        Map<String, String> snapshotOptions = parseData(snapshot.getData());

        Map<String, Object> optionsToRestore = new LinkedHashMap<>();
        keys.forEach(key -> {
            if (snapshotOptions.containsKey(key)) {
                optionsToRestore.put(key, snapshotOptions.get(key));
            }
        });

        if (optionsToRestore.isEmpty()) {
            throw new BadRequestException(
                "None of the specified keys exist in snapshot " + snapshot.getName());
        }

        optionService.save(optionsToRestore);
    }

    @Override
    public OptionSnapshotDTO convertTo(OptionSnapshot snapshot) {
        Assert.notNull(snapshot, "Option snapshot must not be null");

        return new OptionSnapshotDTO().convertFrom(snapshot);
    }

    /**
     * Captures the current raw (persisted) options as an ordered {@code key -> value} map.
     */
    private Map<String, String> currentRawOptions() {
        Map<String, String> result = new LinkedHashMap<>();
        for (Option option : optionService.listAll()) {
            result.put(option.getKey(), option.getValue() == null ? "" : option.getValue());
        }
        return result;
    }

    private String writeData(Map<String, String> options) {
        try {
            return JsonUtils.objectToJson(options);
        } catch (JsonProcessingException e) {
            throw new ServiceException("Failed to serialize option snapshot data", e);
        }
    }

    private Map<String, String> parseData(String data) {
        Map<String, String> result = new LinkedHashMap<>();
        if (!StringUtils.hasText(data)) {
            return result;
        }
        try {
            Map<?, ?> raw = JsonUtils.jsonToObject(data, Map.class);
            raw.forEach((key, value) -> result.put(String.valueOf(key),
                value == null ? null : String.valueOf(value)));
        } catch (IOException e) {
            throw new ServiceException("Failed to deserialize option snapshot data", e);
        }
        return result;
    }
}
