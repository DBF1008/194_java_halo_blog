package run.halo.app.model.vo;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import run.halo.app.model.enums.CommentTargetType;

/**
 * Reply chain of a single comment: its ancestors (root to parent), the comment itself and the
 * descendant tree.
 *
 * @author halo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentReplyChainVO {

    /**
     * Comment target type (source).
     */
    private CommentTargetType targetType;

    /**
     * Ancestor comments ordered from root to direct parent.
     */
    private List<BaseCommentVO> ancestors;

    /**
     * The comment node itself.
     */
    private BaseCommentVO comment;

    /**
     * Descendant comment tree.
     */
    private List<BaseCommentVO> children;
}
