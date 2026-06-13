package run.halo.app.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import run.halo.app.exception.BadRequestException;
import run.halo.app.model.entity.BaseComment;
import run.halo.app.model.entity.CommentBlackList;
import run.halo.app.model.entity.Journal;
import run.halo.app.model.entity.Post;
import run.halo.app.model.entity.Sheet;
import run.halo.app.model.enums.CommentStatus;
import run.halo.app.model.enums.CommentTargetType;
import run.halo.app.model.enums.ModerationAction;
import run.halo.app.model.params.CommentModerationParam;
import run.halo.app.model.params.CommentModerationQuery;
import run.halo.app.model.params.CommentQuery;
import run.halo.app.model.properties.CommentProperties;
import run.halo.app.model.vo.BaseCommentVO;
import run.halo.app.model.vo.CommentBlackListVO;
import run.halo.app.model.vo.CommentModerationResultVO;
import run.halo.app.model.vo.CommentModerationVO;
import run.halo.app.model.vo.CommentReplyChainVO;
import run.halo.app.service.CommentBlackListService;
import run.halo.app.service.CommentModerationService;
import run.halo.app.service.JournalCommentService;
import run.halo.app.service.JournalService;
import run.halo.app.service.OptionService;
import run.halo.app.service.PostCommentService;
import run.halo.app.service.PostService;
import run.halo.app.service.SheetCommentService;
import run.halo.app.service.SheetService;
import run.halo.app.service.base.BaseCommentService;

/**
 * Comment moderation service implementation. Orchestrates the three comment services and the
 * comment black list service. A single private per-item code path ({@link #applyToComment} /
 * {@link #applyToIp}) is the only place that mutates a comment or ip, and it is reused by both the
 * batch and single handling so they behave identically.
 *
 * @author halo
 */
@Service
public class CommentModerationServiceImpl implements CommentModerationService {

    /**
     * Default frequent threshold when the option is absent.
     */
    private static final int DEFAULT_FREQUENT_THRESHOLD = 30;

    /**
     * Maximum depth when walking the reply chain, guarding against cycles.
     */
    private static final int MAX_CHAIN_DEPTH = 100;

    private final Map<CommentTargetType, BaseCommentService<? extends BaseComment>> serviceMap;

    private final CommentBlackListService commentBlackListService;

    private final OptionService optionService;

    private final PostService postService;

    private final SheetService sheetService;

    private final JournalService journalService;

    public CommentModerationServiceImpl(PostCommentService postCommentService,
        SheetCommentService sheetCommentService,
        JournalCommentService journalCommentService,
        CommentBlackListService commentBlackListService,
        OptionService optionService,
        PostService postService,
        SheetService sheetService,
        JournalService journalService) {
        this.commentBlackListService = commentBlackListService;
        this.optionService = optionService;
        this.postService = postService;
        this.sheetService = sheetService;
        this.journalService = journalService;
        this.serviceMap = new EnumMap<>(CommentTargetType.class);
        this.serviceMap.put(CommentTargetType.POST, postCommentService);
        this.serviceMap.put(CommentTargetType.SHEET, sheetCommentService);
        this.serviceMap.put(CommentTargetType.JOURNAL, journalCommentService);
    }

    @Override
    public Page<CommentModerationVO> pageInbox(CommentModerationQuery query, Pageable pageable) {
        Assert.notNull(pageable, "Pageable must not be null");
        CommentModerationQuery moderationQuery =
            query == null ? new CommentModerationQuery() : query;

        Set<CommentTargetType> types = moderationQuery.getTargetType() != null
            ? EnumSet.of(moderationQuery.getTargetType())
            : EnumSet.allOf(CommentTargetType.class);

        CommentQuery commentQuery = new CommentQuery();
        commentQuery.setKeyword(moderationQuery.getKeyword());
        commentQuery.setStatus(moderationQuery.getStatus());

        List<TargetedComment> gathered = gatherComments(commentQuery, types);

        Date now = new Date();
        Map<String, Long> ipCountMap = countByIp(gathered);
        Map<String, CommentBlackList> banMap = loadBanMap(ipCountMap.keySet());
        int frequentThreshold = resolveFrequentThreshold();
        Map<CommentTargetType, Map<Integer, String>> titleMap = resolveTitles(gathered);

        boolean onlyBanned = Boolean.TRUE.equals(moderationQuery.getOnlyBanned());
        boolean onlyFrequent = Boolean.TRUE.equals(moderationQuery.getOnlyFrequent());

        List<CommentModerationVO> items = gathered.stream()
            .map(targeted -> buildModerationVo(targeted, ipCountMap, banMap, frequentThreshold,
                titleMap, now))
            .filter(vo -> !onlyBanned || Boolean.TRUE.equals(vo.getBanned()))
            .filter(vo -> !onlyFrequent || Boolean.TRUE.equals(vo.getFrequent()))
            .sorted(Comparator.comparing(CommentModerationVO::getCreateTime,
                Comparator.nullsLast(Comparator.reverseOrder())))
            .collect(Collectors.toList());

        return paginate(items, pageable);
    }

    @Override
    public CommentReplyChainVO getReplyChain(CommentTargetType targetType, Long commentId) {
        Assert.notNull(targetType, "Comment target type must not be null");
        Assert.notNull(commentId, "Comment id must not be null");

        BaseCommentService<? extends BaseComment> service = getService(targetType);
        BaseComment node = service.getById(commentId);

        Sort sort = Sort.by(Sort.Direction.ASC, "createTime");

        List<BaseCommentVO> ancestors = collectAncestors(service, node);
        BaseCommentVO nodeVo = new BaseCommentVO().convertFrom(node);
        List<BaseCommentVO> children =
            buildChildren(service, node.getPostId(), node.getId(), sort, MAX_CHAIN_DEPTH);

        return CommentReplyChainVO.builder()
            .targetType(targetType)
            .ancestors(ancestors)
            .comment(nodeVo)
            .children(children)
            .build();
    }

    @Override
    public List<CommentBlackListVO> listBlacklist(Boolean onlyBanned) {
        List<CommentBlackList> entries = commentBlackListService.listAll();
        if (CollectionUtils.isEmpty(entries)) {
            return Collections.emptyList();
        }

        Date now = new Date();
        boolean filterBanned = Boolean.TRUE.equals(onlyBanned);

        Map<String, List<BaseComment>> commentsByIp =
            gatherComments(new CommentQuery(), EnumSet.allOf(CommentTargetType.class)).stream()
                .map(targeted -> targeted.comment)
                .filter(comment -> StringUtils.hasText(comment.getIpAddress()))
                .collect(Collectors.groupingBy(BaseComment::getIpAddress));

        return entries.stream()
            .filter(entry -> !filterBanned || isEffective(entry.getBanTime(), now))
            .map(entry -> {
                List<BaseComment> related =
                    commentsByIp.getOrDefault(entry.getIpAddress(), Collections.emptyList());
                Date lastCommentTime = related.stream()
                    .map(BaseComment::getCreateTime)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(null);
                return CommentBlackListVO.builder()
                    .ipAddress(entry.getIpAddress())
                    .banTime(entry.getBanTime())
                    .banned(isEffective(entry.getBanTime(), now))
                    .relatedCommentCount((long) related.size())
                    .lastCommentTime(lastCommentTime)
                    .build();
            })
            .collect(Collectors.toList());
    }

    @Override
    public List<CommentModerationVO> ipHistory(String ipAddress) {
        Assert.hasText(ipAddress, "IP address must not be blank");

        List<TargetedComment> gathered =
            gatherComments(new CommentQuery(), EnumSet.allOf(CommentTargetType.class)).stream()
                .filter(targeted -> ipAddress.equals(targeted.comment.getIpAddress()))
                .collect(Collectors.toList());

        Date now = new Date();
        Map<String, CommentBlackList> banMap = loadBanMap(Collections.singleton(ipAddress));
        int frequentThreshold = resolveFrequentThreshold();
        Map<String, Long> ipCountMap =
            Collections.singletonMap(ipAddress, (long) gathered.size());
        Map<CommentTargetType, Map<Integer, String>> titleMap = resolveTitles(gathered);

        return gathered.stream()
            .map(targeted -> buildModerationVo(targeted, ipCountMap, banMap, frequentThreshold,
                titleMap, now))
            .sorted(Comparator.comparing(CommentModerationVO::getCreateTime,
                Comparator.nullsLast(Comparator.reverseOrder())))
            .collect(Collectors.toList());
    }

    @Override
    public CommentModerationResultVO moderate(CommentModerationParam param) {
        Assert.notNull(param, "Moderation param must not be null");
        Assert.notNull(param.getAction(), "Moderation action must not be null");

        ModerationAction action = param.getAction();
        List<String> succeeded = new LinkedList<>();
        List<CommentModerationResultVO.Failure> failed = new LinkedList<>();

        if (action == ModerationAction.UNBAN) {
            List<String> ipAddresses = param.getIpAddresses() == null
                ? Collections.emptyList() : param.getIpAddresses();
            for (String ipAddress : ipAddresses) {
                try {
                    applyToIp(action, ipAddress);
                    succeeded.add(ipAddress);
                } catch (Exception e) {
                    failed.add(buildFailure(ipAddress, e));
                }
            }
        } else {
            List<CommentModerationParam.Item> items = param.getItems() == null
                ? Collections.emptyList() : param.getItems();
            for (CommentModerationParam.Item item : items) {
                String target = item.getCommentId() == null
                    ? "null" : String.valueOf(item.getCommentId());
                try {
                    applyToComment(action, item.getTargetType(), item.getCommentId(),
                        param.getBanMinutes());
                    succeeded.add(target);
                } catch (Exception e) {
                    failed.add(buildFailure(target, e));
                }
            }
        }

        return CommentModerationResultVO.builder()
            .action(action)
            .succeeded(succeeded)
            .failed(failed)
            .successCount(succeeded.size())
            .failureCount(failed.size())
            .build();
    }

    @Override
    public CommentModerationResultVO handleSingle(ModerationAction action,
        CommentTargetType targetType, Long commentId, Long banMinutes) {
        Assert.notNull(action, "Moderation action must not be null");
        Assert.notNull(targetType, "Comment target type must not be null");
        Assert.notNull(commentId, "Comment id must not be null");

        // Delegate to the batch path so single handling is guaranteed identical to batch handling.
        CommentModerationParam param = new CommentModerationParam();
        param.setAction(action);
        param.setBanMinutes(banMinutes);
        if (action == ModerationAction.UNBAN) {
            BaseComment comment = getService(targetType).getById(commentId);
            param.setIpAddresses(Collections.singletonList(comment.getIpAddress()));
        } else {
            CommentModerationParam.Item item = new CommentModerationParam.Item();
            item.setTargetType(targetType);
            item.setCommentId(commentId);
            param.setItems(Collections.singletonList(item));
        }
        return moderate(param);
    }

    /**
     * The single per-item mutation path for comment-targeted actions. Reused by both batch and
     * single handling.
     */
    private void applyToComment(ModerationAction action, CommentTargetType targetType,
        Long commentId, Long banMinutes) {
        Assert.notNull(commentId, "Comment id must not be null");
        BaseCommentService<? extends BaseComment> service = getService(targetType);
        switch (action) {
            case APPROVE:
                service.updateStatus(commentId, CommentStatus.PUBLISHED);
                break;
            case REJECT:
                service.updateStatus(commentId, CommentStatus.RECYCLE);
                break;
            case BLACKLIST:
                BaseComment comment = service.getById(commentId);
                if (StringUtils.hasText(comment.getIpAddress())) {
                    commentBlackListService.ban(comment.getIpAddress(), banMinutes);
                }
                service.updateStatus(commentId, CommentStatus.RECYCLE);
                break;
            default:
                throw new BadRequestException("不支持的处置动作: " + action);
        }
    }

    /**
     * The single per-ip mutation path. Reused by both batch and single handling.
     */
    private void applyToIp(ModerationAction action, String ipAddress) {
        Assert.hasText(ipAddress, "IP address must not be blank");
        if (action == ModerationAction.UNBAN) {
            commentBlackListService.unban(ipAddress);
        } else {
            throw new BadRequestException("该处置动作不支持按 IP 处理: " + action);
        }
    }

    private List<TargetedComment> gatherComments(CommentQuery query, Set<CommentTargetType> types) {
        List<TargetedComment> result = new ArrayList<>();
        for (CommentTargetType type : types) {
            BaseCommentService<? extends BaseComment> service = getService(type);
            List<? extends BaseComment> comments =
                service.pageBy(query, Pageable.unpaged()).getContent();
            for (BaseComment comment : comments) {
                result.add(new TargetedComment(type, comment));
            }
        }
        return result;
    }

    private Map<String, Long> countByIp(List<TargetedComment> gathered) {
        return gathered.stream()
            .map(targeted -> targeted.comment.getIpAddress())
            .filter(StringUtils::hasText)
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }

    private Map<String, CommentBlackList> loadBanMap(Set<String> ipAddresses) {
        if (CollectionUtils.isEmpty(ipAddresses)) {
            return Collections.emptyMap();
        }
        return commentBlackListService.listByIpAddressIn(ipAddresses).stream()
            .collect(Collectors.toMap(CommentBlackList::getIpAddress, Function.identity(),
                (first, second) -> first));
    }

    private int resolveFrequentThreshold() {
        return optionService.getByPropertyOrDefault(CommentProperties.COMMENT_RANGE, Integer.class,
            DEFAULT_FREQUENT_THRESHOLD);
    }

    private Map<CommentTargetType, Map<Integer, String>> resolveTitles(
        List<TargetedComment> gathered) {
        Map<CommentTargetType, Set<Integer>> idsByType = new EnumMap<>(CommentTargetType.class);
        for (TargetedComment targeted : gathered) {
            idsByType.computeIfAbsent(targeted.targetType, type -> new HashSet<>())
                .add(targeted.comment.getPostId());
        }

        Map<CommentTargetType, Map<Integer, String>> titleMap =
            new EnumMap<>(CommentTargetType.class);

        Set<Integer> postIds = idsByType.get(CommentTargetType.POST);
        if (!CollectionUtils.isEmpty(postIds)) {
            titleMap.put(CommentTargetType.POST, postService.listAllByIds(postIds).stream()
                .collect(Collectors.toMap(Post::getId, Post::getTitle)));
        }

        Set<Integer> sheetIds = idsByType.get(CommentTargetType.SHEET);
        if (!CollectionUtils.isEmpty(sheetIds)) {
            titleMap.put(CommentTargetType.SHEET, sheetService.listAllByIds(sheetIds).stream()
                .collect(Collectors.toMap(Sheet::getId, Sheet::getTitle)));
        }

        Set<Integer> journalIds = idsByType.get(CommentTargetType.JOURNAL);
        if (!CollectionUtils.isEmpty(journalIds)) {
            titleMap.put(CommentTargetType.JOURNAL, journalService.listAllByIds(journalIds).stream()
                .collect(Collectors.toMap(Journal::getId, journal -> "日志 #" + journal.getId())));
        }

        return titleMap;
    }

    private CommentModerationVO buildModerationVo(TargetedComment targeted,
        Map<String, Long> ipCountMap, Map<String, CommentBlackList> banMap, int frequentThreshold,
        Map<CommentTargetType, Map<Integer, String>> titleMap, Date now) {
        BaseComment comment = targeted.comment;
        String ipAddress = comment.getIpAddress();
        long ipCount = ipAddress == null ? 0L : ipCountMap.getOrDefault(ipAddress, 0L);
        CommentBlackList blackList = ipAddress == null ? null : banMap.get(ipAddress);
        Date banTime = blackList == null ? null : blackList.getBanTime();
        boolean banned = isEffective(banTime, now);
        boolean frequent = ipCount >= frequentThreshold;

        return CommentModerationVO.builder()
            .targetType(targeted.targetType)
            .targetId(comment.getPostId())
            .targetTitle(lookupTitle(titleMap, targeted.targetType, comment.getPostId()))
            .id(comment.getId())
            .author(comment.getAuthor())
            .email(comment.getEmail())
            .ipAddress(ipAddress)
            .content(comment.getContent())
            .status(comment.getStatus())
            .parentId(comment.getParentId())
            .createTime(comment.getCreateTime())
            .isAdmin(comment.getIsAdmin())
            .banned(banned)
            .banTime(banTime)
            .frequent(frequent)
            .ipCommentCount(ipCount)
            .recommendedAction(recommendedAction(comment.getStatus(), banned, frequent))
            .build();
    }

    private String lookupTitle(Map<CommentTargetType, Map<Integer, String>> titleMap,
        CommentTargetType targetType, Integer targetId) {
        Map<Integer, String> titles = titleMap.get(targetType);
        if (titles != null && titles.get(targetId) != null) {
            return titles.get(targetId);
        }
        return defaultTitle(targetType, targetId);
    }

    private String defaultTitle(CommentTargetType targetType, Integer targetId) {
        switch (targetType) {
            case POST:
                return "文章 #" + targetId;
            case SHEET:
                return "页面 #" + targetId;
            case JOURNAL:
                return "日志 #" + targetId;
            default:
                return "#" + targetId;
        }
    }

    private ModerationAction recommendedAction(CommentStatus status, boolean banned,
        boolean frequent) {
        if (banned || frequent) {
            return ModerationAction.BLACKLIST;
        }
        if (status == CommentStatus.AUDITING) {
            return ModerationAction.APPROVE;
        }
        return null;
    }

    private List<BaseCommentVO> collectAncestors(BaseCommentService<? extends BaseComment> service,
        BaseComment node) {
        LinkedList<BaseCommentVO> ancestors = new LinkedList<>();
        Set<Long> visited = new HashSet<>();
        Long parentId = node.getParentId();
        int guard = MAX_CHAIN_DEPTH;
        while (parentId != null && parentId > 0 && guard-- > 0 && visited.add(parentId)) {
            BaseComment parent = service.fetchById(parentId).orElse(null);
            if (parent == null) {
                break;
            }
            ancestors.addFirst(new BaseCommentVO().convertFrom(parent));
            parentId = parent.getParentId();
        }
        return ancestors;
    }

    private List<BaseCommentVO> buildChildren(BaseCommentService<? extends BaseComment> service,
        Integer targetId, Long parentId, Sort sort, int depth) {
        if (depth <= 0) {
            return Collections.emptyList();
        }
        List<? extends BaseComment> children = service.listChildrenBy(targetId, parentId, sort);
        if (CollectionUtils.isEmpty(children)) {
            return Collections.emptyList();
        }
        List<BaseCommentVO> result = new ArrayList<>(children.size());
        for (BaseComment child : children) {
            BaseCommentVO vo = new BaseCommentVO().convertFrom(child);
            vo.setChildren(buildChildren(service, targetId, child.getId(), sort, depth - 1));
            result.add(vo);
        }
        return result;
    }

    private Page<CommentModerationVO> paginate(List<CommentModerationVO> items, Pageable pageable) {
        if (pageable.isUnpaged()) {
            return new PageImpl<>(items);
        }
        int total = items.size();
        int start = (int) pageable.getOffset();
        if (start >= total) {
            return new PageImpl<>(Collections.emptyList(), pageable, total);
        }
        int end = Math.min(start + pageable.getPageSize(), total);
        return new PageImpl<>(items.subList(start, end), pageable, total);
    }

    private CommentModerationResultVO.Failure buildFailure(String target, Exception e) {
        return CommentModerationResultVO.Failure.builder()
            .target(target)
            .reason(e.getMessage())
            .build();
    }

    private boolean isEffective(Date banTime, Date now) {
        return banTime != null && banTime.after(now);
    }

    private BaseCommentService<? extends BaseComment> getService(CommentTargetType targetType) {
        BaseCommentService<? extends BaseComment> service = serviceMap.get(targetType);
        if (service == null) {
            throw new BadRequestException("不支持的评论类型: " + targetType);
        }
        return service;
    }

    /**
     * A comment paired with its resolved target type (source).
     */
    private static final class TargetedComment {

        private final CommentTargetType targetType;

        private final BaseComment comment;

        private TargetedComment(CommentTargetType targetType, BaseComment comment) {
            this.targetType = targetType;
            this.comment = comment;
        }
    }
}
