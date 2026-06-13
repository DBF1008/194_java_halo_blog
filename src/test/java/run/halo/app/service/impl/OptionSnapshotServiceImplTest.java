package run.halo.app.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import run.halo.app.exception.AlreadyExistsException;
import run.halo.app.exception.BadRequestException;
import run.halo.app.model.dto.OptionSnapshotDTO;
import run.halo.app.model.entity.Option;
import run.halo.app.model.entity.OptionSnapshot;
import run.halo.app.model.enums.OptionDiffType;
import run.halo.app.model.params.OptionSnapshotParam;
import run.halo.app.model.vo.OptionSnapshotDetailVO;
import run.halo.app.model.vo.OptionSnapshotDiffVO;
import run.halo.app.repository.OptionSnapshotRepository;
import run.halo.app.service.OptionService;
import run.halo.app.utils.JsonUtils;

/**
 * Option snapshot service test.
 *
 * @author halo
 */
class OptionSnapshotServiceImplTest {

    @Mock
    OptionSnapshotRepository optionSnapshotRepository;

    @Mock
    OptionService optionService;

    @InjectMocks
    OptionSnapshotServiceImpl optionSnapshotService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void createSnapshotCapturesCurrentRawOptions() throws Exception {
        given(optionSnapshotRepository.existsByName("snap1")).willReturn(false);
        given(optionService.listAll())
            .willReturn(Arrays.asList(option("blog_title", "Halo"), option("blog_url", "http://x")));
        given(optionSnapshotRepository.save(any(OptionSnapshot.class)))
            .willAnswer(invocation -> invocation.getArgument(0));

        OptionSnapshotParam param = new OptionSnapshotParam();
        param.setName("snap1");
        param.setDescription("first snapshot");

        OptionSnapshotDTO dto = optionSnapshotService.createSnapshot(param);

        assertEquals("snap1", dto.getName());

        ArgumentCaptor<OptionSnapshot> captor = ArgumentCaptor.forClass(OptionSnapshot.class);
        then(optionSnapshotRepository).should().save(captor.capture());

        Map<?, ?> savedData = JsonUtils.jsonToObject(captor.getValue().getData(), Map.class);
        assertEquals(2, savedData.size());
        assertEquals("Halo", savedData.get("blog_title"));
        assertEquals("http://x", savedData.get("blog_url"));
    }

    @Test
    void createSnapshotWithDuplicateNameThrows() {
        given(optionSnapshotRepository.existsByName("dup")).willReturn(true);

        OptionSnapshotParam param = new OptionSnapshotParam();
        param.setName("dup");

        assertThrows(AlreadyExistsException.class,
            () -> optionSnapshotService.createSnapshot(param));

        then(optionSnapshotRepository).should(never()).save(any(OptionSnapshot.class));
    }

    @Test
    void getDetailVoByIdReturnsCapturedData() throws Exception {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("a", "1");
        data.put("b", "2");
        given(optionSnapshotRepository.findById(1)).willReturn(Optional.of(snapshot(1, "snap", data)));

        OptionSnapshotDetailVO detailVo = optionSnapshotService.getDetailVoById(1);

        assertEquals("snap", detailVo.getName());
        assertEquals(2, detailVo.getData().size());
        assertEquals("1", detailVo.getData().get("a"));
        assertEquals("2", detailVo.getData().get("b"));
    }

    @Test
    void diffClassifiesEachKey() throws Exception {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("a", "1");
        data.put("b", "2");
        data.put("c", "3");
        given(optionSnapshotRepository.findById(1)).willReturn(Optional.of(snapshot(1, "snap", data)));
        given(optionService.listAll())
            .willReturn(Arrays.asList(option("a", "1"), option("b", "20"), option("d", "4")));

        OptionSnapshotDiffVO diff = optionSnapshotService.diff(1);

        assertEquals(1, diff.getUnchanged());
        assertEquals(1, diff.getModified());
        assertEquals(1, diff.getDeleted());
        assertEquals(1, diff.getAdded());
        assertEquals(4, diff.getItems().size());

        Map<String, OptionSnapshotDiffVO.DiffItem> byKey = diff.getItems().stream()
            .collect(Collectors.toMap(OptionSnapshotDiffVO.DiffItem::getKey, item -> item));

        assertEquals(OptionDiffType.UNCHANGED, byKey.get("a").getType());

        OptionSnapshotDiffVO.DiffItem modified = byKey.get("b");
        assertEquals(OptionDiffType.MODIFIED, modified.getType());
        assertEquals("2", modified.getSnapshotValue());
        assertEquals("20", modified.getCurrentValue());

        assertEquals(OptionDiffType.DELETED, byKey.get("c").getType());
        assertEquals(OptionDiffType.ADDED, byKey.get("d").getType());
    }

    @Test
    void applyKeysRestoresOnlySelectedKeysThroughOptionService() throws Exception {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("a", "1");
        data.put("b", "2");
        data.put("c", "3");
        given(optionSnapshotRepository.findById(1)).willReturn(Optional.of(snapshot(1, "snap", data)));

        optionSnapshotService.applyKeys(1, Arrays.asList("b", "c"));

        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        then(optionService).should().save(captor.capture());

        Map<?, ?> saved = captor.getValue();
        assertEquals(2, saved.size());
        assertEquals("2", saved.get("b"));
        assertEquals("3", saved.get("c"));
        assertFalse(saved.containsKey("a"));
    }

    @Test
    void applyRestoresAllSnapshotKeysThroughOptionService() throws Exception {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("a", "1");
        data.put("b", "2");
        data.put("c", "3");
        given(optionSnapshotRepository.findById(1)).willReturn(Optional.of(snapshot(1, "snap", data)));

        optionSnapshotService.apply(1);

        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        then(optionService).should().save(captor.capture());

        Map<?, ?> saved = captor.getValue();
        assertEquals(3, saved.size());
        assertEquals("1", saved.get("a"));
        assertEquals("2", saved.get("b"));
        assertEquals("3", saved.get("c"));
    }

    @Test
    void applyKeysWithEmptyKeysThrowsAndDoesNotSave() {
        assertThrows(BadRequestException.class,
            () -> optionSnapshotService.applyKeys(1, Collections.emptyList()));

        then(optionService).should(never()).save(any(Map.class));
    }

    @Test
    void applyKeysWithNoMatchingKeysThrowsAndDoesNotSave() throws Exception {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("a", "1");
        given(optionSnapshotRepository.findById(1)).willReturn(Optional.of(snapshot(1, "snap", data)));

        assertThrows(BadRequestException.class,
            () -> optionSnapshotService.applyKeys(1, Arrays.asList("x", "y")));

        then(optionService).should(never()).save(any(Map.class));
    }

    private Option option(String key, String value) {
        return new Option(key, value);
    }

    private OptionSnapshot snapshot(Integer id, String name, Map<String, String> data)
        throws Exception {
        OptionSnapshot snapshot = new OptionSnapshot();
        snapshot.setId(id);
        snapshot.setName(name);
        snapshot.setData(JsonUtils.objectToJson(data));
        return snapshot;
    }
}
