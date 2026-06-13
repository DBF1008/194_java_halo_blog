package run.halo.app.model.dto;

import java.util.Date;
import lombok.Data;
import run.halo.app.model.dto.base.OutputConverter;
import run.halo.app.model.entity.OptionSnapshot;

/**
 * Option snapshot output dto (lightweight, without the captured data).
 *
 * @author halo
 */
@Data
public class OptionSnapshotDTO implements OutputConverter<OptionSnapshotDTO, OptionSnapshot> {

    private Integer id;

    private String name;

    private String description;

    private Date createTime;

    private Date updateTime;
}
