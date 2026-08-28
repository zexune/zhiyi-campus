package com.zhiyi.module.item.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.enums.ItemStatus;
import com.zhiyi.common.enums.ItemType;
import com.zhiyi.common.enums.ModerationStatus;
import com.zhiyi.common.enums.ViolationSource;
import com.zhiyi.common.enums.ViolationStatus;
import com.zhiyi.module.admin.entity.ViolationReport;
import com.zhiyi.module.admin.mapper.ViolationReportMapper;
import com.zhiyi.module.item.dto.PublishItemDTO;
import com.zhiyi.module.item.dto.ReportItemDTO;
import com.zhiyi.module.item.entity.Category;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.CategoryMapper;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.item.vo.ItemCardVO;
import com.zhiyi.module.item.vo.UploadImageVO;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 商品发布、整改、重新上架、举报与本地内容检测。
 *
 * 并发边界：item_reservation 已淘汰。编辑/删除/下架对 RESERVED/SOLD 商品
 * 通过商品状态条件迁移拒绝（进行中订单期间商品不可变更，与下单路径的商品行锁互斥）。
 * 任何影响 Feed 资格/筛选/排序的编辑或重新上架都会分配新的 listing_revision，
 * 使商品退出旧游标快照（可从旧链消失，不能以新位置再次出现）。
 */
@Service
@RequiredArgsConstructor
public class ItemPublishService {

    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final Map<String, String> REPORT_LABELS = Map.of(
            "PRICE_FRAUD", "疑似虚假价格",
            "PROHIBITED_ITEM", "疑似违禁商品",
            "IMAGE_VIOLATION", "图片内容违规",
            "ADVERTISING", "广告或站外引流",
            "OTHER", "其他问题"
    );

    private final ItemMapper itemMapper;
    private final CategoryMapper categoryMapper;
    private final ViolationReportMapper violationReportMapper;
    private final MarketplaceService marketplaceService;
    private final SysUserMapper userMapper;
    private final LocalContentAnalyzer contentAnalyzer;
    private final ItemTagService itemTagService;
    private final com.zhiyi.module.item.mapper.ItemViewStatMapper viewStatMapper;
    private final com.zhiyi.common.storage.LocalImageStorage imageStorage;

    public UploadImageVO uploadImage(MultipartFile file) {
        return new UploadImageVO(imageStorage.store(file, "items", MAX_IMAGE_BYTES));
    }

    @Transactional
    public ItemCardVO publish(Long publisherId, PublishItemDTO dto) {
        SysUser publisher = requirePublisher(publisherId);
        Category category = requireCategory(dto.getCategoryId());
        validateImages(dto.getImages());

        ContentCheck check = checkContent(dto, category);
        Item item = buildItem(publisherId, publisher.getSchoolId(), dto);
        solidifyPublisherKeys(item, publisher);
        item.setStatus(ItemStatus.ON_SALE);
        item.setModerationStatus(check.risky() ? ModerationStatus.PENDING : ModerationStatus.PASSED);
        item.setListingRevision(allocateListingRevision());
        itemMapper.insert(item);
        // 初始化浏览统计行（独立统计表，保证浏览量排序 keyset 可用且计数单调）
        com.zhiyi.module.item.entity.ItemViewStat stat = new com.zhiyi.module.item.entity.ItemViewStat();
        stat.setItemId(item.getId());
        stat.setViewCount(0L);
        viewStatMapper.insert(stat);
        itemTagService.replaceTags(item.getId(), check.tags());
        if (check.risky()) {
            saveReview(publisherId, null, item.getId(), dto, ViolationSource.LOCAL_RULE, "KEYWORD_MATCH",
                    check.reason(), check.matchedRules(), check.ruleVersion());
        }
        return marketplaceService.getSnapshot(item.getId(), publisherId);
    }

    /**
     * 编辑商品（B4/B5 根因修复）：无锁读只用于归属与快速校验；
     * 落库是"只含本次编辑字段的 patch 实体 + 条件 UPDATE"
     * （WHERE status IN (ON_SALE, OFF_SHELF) 且 moderation_status 等于读取值）。
     * 并发下单置 RESERVED 使状态条件不匹配；并发违规确认把商品压到
     * OFF_SHELF+REJECTED（状态仍在 IN 列表内）由 moderation 重检拒绝，
     * 否则非整改分支会把 PASSED 写回、吞掉 REJECTED（违反 I24）。
     * 两种竞态均 0 行 → CONFLICT，读取时的 status/moderation_status 永不被写回。
     */
    @Transactional
    public ItemCardVO update(Long publisherId, Long itemId, PublishItemDTO dto) {
        Item item = requireOwnedItem(publisherId, itemId);
        if (item.getStatus() == ItemStatus.SOLD) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "已售出的商品不能编辑");
        }
        if (item.getStatus() == ItemStatus.RESERVED) {
            throw new BusinessException(ResultCode.CONFLICT, "商品存在进行中的订单，暂不能修改");
        }
        if (item.getModerationStatus() == ModerationStatus.PENDING) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "商品正在审核中，暂不能重复修改");
        }

        Category category = requireCategory(dto.getCategoryId());
        validateImages(dto.getImages());
        boolean correction = item.getModerationStatus() == ModerationStatus.REJECTED;
        ContentCheck check = checkContent(dto, category);

        // patch 只携带本次编辑的资料字段 + 目标审核状态；status 除整改分支外不写
        Item patch = new Item();
        applyContent(patch, dto);
        patch.setListingRevision(allocateListingRevision());
        if (correction) {
            patch.setStatus(ItemStatus.OFF_SHELF);
            patch.setModerationStatus(ModerationStatus.PENDING);
        } else {
            patch.setModerationStatus(check.risky() ? ModerationStatus.PENDING : ModerationStatus.PASSED);
        }

        int updated = itemMapper.update(patch, new LambdaUpdateWrapper<Item>()
                .eq(Item::getId, itemId)
                .eq(Item::getPublisherId, publisherId)
                .in(Item::getStatus, ItemStatus.ON_SALE, ItemStatus.OFF_SHELF)
                // 违规确认投影只把 status 压到 OFF_SHELF（仍在 IN 列表内），
                // 必须以读取时的 moderation 重检拒绝并发 REJECTED 迁移（I24）
                .eq(Item::getModerationStatus, item.getModerationStatus()));
        if (updated == 0) {
            // 竞态窗口：无锁读之后商品被并发迁移到 RESERVED/SOLD/REJECTED 等不可编辑状态
            throw new BusinessException(ResultCode.CONFLICT, "商品状态已变化，请刷新后重试");
        }

        if (correction) {
            String reason = check.risky()
                    ? check.reason()
                    : "卖家已提交整改内容，等待管理员复核";
            saveReview(publisherId, null, itemId, dto, ViolationSource.CORRECTION, "CORRECTION_REVIEW",
                    reason, check.matchedRules(), check.ruleVersion());
        } else if (check.risky()) {
            saveReview(publisherId, null, itemId, dto, ViolationSource.LOCAL_RULE, "KEYWORD_MATCH",
                    check.reason(), check.matchedRules(), check.ruleVersion());
        }
        itemTagService.replaceTags(itemId, check.tags());
        return marketplaceService.getSnapshot(itemId, publisherId);
    }

    /**
     * 重新上架：同样以条件 UPDATE（WHERE status='OFF_SHELF' 且 moderation 等于
     * 读取值——前置校验保证读取值为 PASSED）落库；刷新发布者层级键并分配新
     * listing_revision。moderation 重检拒绝并发违规确认（REJECTED 投影不改
     * OFF_SHELF 状态，靠 ne(PENDING) 挡不住 REJECTED→PASSED 写回）。
     */
    @Transactional
    public ItemCardVO relist(Long publisherId, Long itemId) {
        Item item = requireOwnedItem(publisherId, itemId);
        if (item.getStatus() != ItemStatus.OFF_SHELF) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "只有已下架商品可以重新上架");
        }
        if (item.getModerationStatus() == ModerationStatus.PENDING) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "商品正在审核中");
        }
        if (item.getModerationStatus() == ModerationStatus.REJECTED) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该商品因内容违规下架，请先整改或申诉");
        }

        SysUser publisher = requirePublisher(publisherId);
        Category category = requireCategory(item.getCategoryId());
        PublishItemDTO dto = toReviewDTO(item);
        ContentCheck check = checkContent(dto, category);

        Item patch = new Item();
        patch.setListingRevision(allocateListingRevision());
        patch.setPublisherCampusKey(locationKey(publisher.getCampus()));
        patch.setPublisherDormitoryKey(locationKey(publisher.getDormitory()));
        if (check.risky()) {
            // 命中规则：转入人工审核，保持 OFF_SHELF
            patch.setModerationStatus(ModerationStatus.PENDING);
        } else {
            patch.setStatus(ItemStatus.ON_SALE);
            patch.setModerationStatus(ModerationStatus.PASSED);
        }

        int updated = itemMapper.update(patch, new LambdaUpdateWrapper<Item>()
                .eq(Item::getId, itemId)
                .eq(Item::getPublisherId, publisherId)
                .eq(Item::getStatus, ItemStatus.OFF_SHELF)
                .eq(Item::getModerationStatus, item.getModerationStatus()));
        if (updated == 0) {
            throw new BusinessException(ResultCode.CONFLICT, "商品状态已变化，请刷新后重试");
        }

        if (check.risky()) {
            saveReview(publisherId, null, itemId, dto, ViolationSource.LOCAL_RULE, "KEYWORD_MATCH",
                    check.reason(), check.matchedRules(), check.ruleVersion());
        }
        itemTagService.replaceTags(itemId, check.tags());
        return marketplaceService.getSnapshot(itemId, publisherId);
    }

    /**
     * 用户举报不会自动隐藏商品，避免恶意举报造成下架；最终状态由管理员决定。
     */
    @Transactional
    public void report(Long reporterId, Long itemId, ReportItemDTO dto) {
        Item item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在");
        }
        marketplaceService.requireVisibleItem(reporterId, itemId);
        if (Objects.equals(item.getPublisherId(), reporterId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能举报自己发布的商品");
        }
        long existing = violationReportMapper.selectCount(new LambdaQueryWrapper<ViolationReport>()
                .eq(ViolationReport::getItemId, itemId)
                .eq(ViolationReport::getReporterId, reporterId)
                .eq(ViolationReport::getSource, ViolationSource.USER_REPORT)
                .eq(ViolationReport::getStatus, ViolationStatus.PENDING));
        if (existing > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "你已经举报过该商品，管理员正在处理");
        }

        ViolationReport report = new ViolationReport();
        report.setUserId(item.getPublisherId());
        report.setReporterId(reporterId);
        report.setItemId(itemId);
        report.setOriginalTitle(item.getTitle());
        report.setOriginalDescription(item.getDescription());
        report.setSource(ViolationSource.USER_REPORT);
        report.setViolationType(dto.type());
        String label = REPORT_LABELS.getOrDefault(dto.type(), "其他问题");
        report.setViolationReason(StringUtils.hasText(dto.details())
                ? label + "：" + dto.details().trim()
                : label);
        report.setMatchedRules(List.of());
        report.setRuleVersion(null);
        report.setStatus(ViolationStatus.PENDING);
        violationReportMapper.insert(report);
    }

    private void saveReview(Long sellerId,
                            Long reporterId,
                            Long itemId,
                            PublishItemDTO dto,
                            ViolationSource source,
                            String violationType,
                            String reason,
                            List<String> matchedRules,
                            String ruleVersion) {
        long existing = violationReportMapper.selectCount(new LambdaQueryWrapper<ViolationReport>()
                .eq(ViolationReport::getItemId, itemId)
                .in(ViolationReport::getSource, ViolationSource.LOCAL_RULE, ViolationSource.CORRECTION)
                .eq(ViolationReport::getStatus, ViolationStatus.PENDING));
        if (existing > 0) {
            return;
        }
        ViolationReport report = new ViolationReport();
        report.setUserId(sellerId);
        report.setReporterId(reporterId);
        report.setItemId(itemId);
        report.setOriginalTitle(dto.getTitle().trim());
        report.setOriginalDescription(dto.getDescription().trim());
        report.setSource(source);
        report.setViolationType(violationType);
        report.setViolationReason(reason);
        report.setMatchedRules(List.copyOf(matchedRules));
        report.setRuleVersion(ruleVersion);
        report.setStatus(ViolationStatus.PENDING);
        violationReportMapper.insert(report);
    }

    private Item buildItem(Long publisherId,
                           Long schoolId,
                           PublishItemDTO dto) {
        Item item = new Item();
        item.setPublisherId(publisherId);
        item.setSchoolId(schoolId);
        applyContent(item, dto);
        item.setIsDeleted(false);
        item.setFeedKey(ThreadLocalRandom.current().nextLong(Long.MAX_VALUE));
        return item;
    }

    private void applyContent(Item item, PublishItemDTO dto) {
        item.setType(ItemType.from(dto.getType()));
        item.setTitle(dto.getTitle().trim());
        item.setDescription(dto.getDescription().trim());
        item.setCategoryId(dto.getCategoryId());
        item.setPrice(normalizePrice(dto));
        item.setImages(List.copyOf(dto.getImages()));
        item.setTradeLocation(trimToNull(dto.getTradeLocation()));
        item.setPickupLocation(trimToNull(dto.getPickupLocation()));
        item.setDeliveryLocation(trimToNull(dto.getDeliveryLocation()));
    }

    /** 发布时固化发布者层级键（与 sys_user 生成列同规则：TRIM + 去空格 + 小写）。 */
    private void solidifyPublisherKeys(Item item, SysUser publisher) {
        item.setPublisherCampusKey(locationKey(publisher.getCampus()));
        item.setPublisherDormitoryKey(locationKey(publisher.getDormitory()));
    }

    private String locationKey(String value) {
        return value == null || value.isBlank() ? null : value.trim().replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    /**
     * 分配全局单调 listing_revision（同一事务内 bump + 回读，行锁保证串行）。
     * 返回新值由调用方写入 patch/新实体——内容编辑影响 Feed 资格/筛选/排序，
     * 新 revision 使商品退出旧游标快照（可消失，不能换位置重现）。
     */
    private long allocateListingRevision() {
        itemMapper.bumpListingRevision();
        return itemMapper.currentListingRevision();
    }

    private SysUser requirePublisher(Long publisherId) {
        SysUser publisher = userMapper.selectById(publisherId);
        if (publisher == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (publisher.getSchoolId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请先设置所属学校");
        }
        return publisher;
    }

    private Item requireOwnedItem(Long userId, Long itemId) {
        Item item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在");
        }
        if (!Objects.equals(item.getPublisherId(), userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能操作自己发布的商品");
        }
        return item;
    }

    private Category requireCategory(Long categoryId) {
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品分类不存在");
        }
        return category;
    }

    private void validateImages(List<String> images) {
        for (String image : images) {
            if (!StringUtils.hasText(image) || !image.startsWith("/uploads/items/")) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "图片地址必须来自平台上传接口");
            }
        }
    }

    private PublishItemDTO toReviewDTO(Item item) {
        PublishItemDTO dto = new PublishItemDTO();
        dto.setType(item.getType().code());
        dto.setTitle(item.getTitle());
        dto.setDescription(item.getDescription());
        dto.setCategoryId(item.getCategoryId());
        dto.setPrice(item.getPrice());
        dto.setImages(item.getImages());
        dto.setTradeLocation(item.getTradeLocation());
        dto.setPickupLocation(item.getPickupLocation());
        dto.setDeliveryLocation(item.getDeliveryLocation());
        // 重上架复检时保留用户已选标签（视为用户提供的 tags，走同一套清洗与规则审查）
        dto.setTags(itemTagService.tagsByItemIds(java.util.Set.of(item.getId()))
                .getOrDefault(item.getId(), List.of()));
        return dto;
    }

    /**
     * 内容审查汇总 = 正文/地点分析 + 用户自定义标签清洗。
     * dto.tags 为 null 表示调用方未提供（旧客户端），沿用系统生成标签；
     * 非 null（含空数组）以用户选择为准，但必须通过规则审查，命中即整单转人工。
     */
    private ContentCheck checkContent(PublishItemDTO dto, Category category) {
        LocalContentAnalyzer.AnalysisResult analysis = contentAnalyzer.analyze(dto, category);
        if (dto.getTags() == null) {
            return new ContentCheck(analysis.risky(), analysis.reason(), analysis.matchedRules(),
                    analysis.ruleVersion(), analysis.tags());
        }
        LocalContentAnalyzer.TagCheck tagCheck = contentAnalyzer.sanitizeUserTags(dto.getTags());
        List<String> matchedRules = new java.util.ArrayList<>(analysis.matchedRules());
        for (String code : tagCheck.matchedRules()) {
            if (!matchedRules.contains(code)) matchedRules.add(code);
        }
        String reason = java.util.stream.Stream.of(analysis.reason(), tagCheck.reason())
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.joining("；"));
        boolean risky = analysis.risky() || tagCheck.risky();
        List<String> tags = risky ? List.of() : tagCheck.tags();
        return new ContentCheck(risky, reason, matchedRules, analysis.ruleVersion(), tags);
    }

    /** 内容审查汇总结果：tags 为最终应写入的商品标签集合 */
    private record ContentCheck(boolean risky,
                                String reason,
                                List<String> matchedRules,
                                String ruleVersion,
                                List<String> tags) {
    }

    /** 标签建议：按标题与分类生成候选，仅供前端选择，不落库 */
    public List<String> suggestTags(String title, Long categoryId) {
        Category category = categoryId == null ? null : requireCategory(categoryId);
        return contentAnalyzer.suggestTags(title, category);
    }

    private BigDecimal normalizePrice(PublishItemDTO dto) {
        return ItemType.from(dto.getType()) == ItemType.SWAP ? null : dto.getPrice().setScale(2);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
