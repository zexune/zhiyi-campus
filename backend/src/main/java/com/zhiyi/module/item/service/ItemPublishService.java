package com.zhiyi.module.item.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.module.admin.entity.ViolationReport;
import com.zhiyi.module.admin.mapper.ViolationReportMapper;
import com.zhiyi.module.item.dto.PublishItemDTO;
import com.zhiyi.module.item.entity.Category;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.CategoryMapper;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.item.vo.ItemCardVO;
import com.zhiyi.module.item.vo.UploadImageVO;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 模块二：商品发布、图片上传与 AI 审核/打标。
 */
@Service
@RequiredArgsConstructor
public class ItemPublishService {

    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private final ItemMapper itemMapper;
    private final CategoryMapper categoryMapper;
    private final ViolationReportMapper violationReportMapper;
    private final MarketplaceService marketplaceService;
    private final SysUserMapper userMapper;
    private final JsonMapper objectMapper;
    private final PlatformTransactionManager transactionManager;
    private final AiReviewService aiReviewService;

    @Value("${zhiyi.upload-path:./uploads}")
    private String uploadPath;

    public UploadImageVO uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请选择要上传的图片");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "单张图片不能超过5MB");
        }
        String extension = extensionOf(file.getOriginalFilename(), file.getContentType());
        String day = LocalDate.now().format(DAY_FORMATTER);
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path targetDir = Path.of(uploadPath, "items", day).toAbsolutePath().normalize();
        Path target = targetDir.resolve(filename).normalize();
        if (!target.startsWith(targetDir)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文件名非法");
        }
        try {
            Files.createDirectories(targetDir);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.SERVER_ERROR, "图片保存失败");
        }
        return new UploadImageVO("/uploads/items/" + day + "/" + filename);
    }

    @Transactional
    public ItemCardVO publish(Long publisherId, PublishItemDTO dto) {
        SysUser publisher = userMapper.selectById(publisherId);
        if (publisher == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (publisher.getSchoolId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请先设置所属学校");
        }
        Category category = categoryMapper.selectById(dto.getCategoryId());
        if (category == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品分类不存在");
        }
        validateImages(dto.getImages());

        ReviewResult review = review(dto, category);

        if (review.violation()) {
            // 独立事务：确保 item + violation_report 提交后再抛异常
            TransactionTemplate newTx = new TransactionTemplate(transactionManager);
            newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            Long savedItemId = newTx.execute(status -> {
                Item item = buildItem(publisherId, publisher.getSchoolId(), dto, review);
                item.setStatus("OFF_SHELF");
                item.setAiReviewed(true);
                itemMapper.insert(item);
                saveViolationReport(publisherId, item.getId(), dto, review, false);
                return item.getId();
            });
            throw new BusinessException(ResultCode.AI_VIOLATION, review.reason());
        }

        // 合规商品正常发布
        Item item = buildItem(publisherId, publisher.getSchoolId(), dto, review);
        item.setStatus("ON_SALE");
        item.setAiReviewed(!review.reviewError());
        itemMapper.insert(item);
        if (review.reviewError()) {
            saveViolationReport(publisherId, item.getId(), dto, review, true);
        }
        return marketplaceService.getSnapshot(item.getId(), publisherId);
    }

    @Transactional
    public ItemCardVO update(Long publisherId, Long itemId, PublishItemDTO dto) {
        Item item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在");
        }
        if (!Objects.equals(item.getPublisherId(), publisherId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能编辑自己发布的商品");
        }
        if (!List.of("ON_SALE", "OFF_SHELF").contains(item.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "交易中或已售出的商品不能编辑");
        }

        Category category = categoryMapper.selectById(dto.getCategoryId());
        if (category == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品分类不存在");
        }
        validateImages(dto.getImages());
        ReviewResult review = review(dto, category);
        if (review.violation()) {
            TransactionTemplate newTx = new TransactionTemplate(transactionManager);
            newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            newTx.executeWithoutResult(status -> {
                item.setStatus("OFF_SHELF");
                item.setAiReviewed(true);
                itemMapper.updateById(item);
                saveViolationReport(publisherId, item.getId(), dto, review, false);
            });
            throw new BusinessException(ResultCode.AI_VIOLATION, review.reason());
        }

        item.setType(dto.getType());
        item.setTitle(dto.getTitle().trim());
        item.setDescription(dto.getDescription().trim());
        item.setCategoryId(dto.getCategoryId());
        item.setPrice(normalizePrice(dto));
        item.setImages(toJson(dto.getImages()));
        item.setAiTags(toJson(review.tags()));
        item.setAiReviewed(!review.reviewError());
        item.setTradeLocation(trimToNull(dto.getTradeLocation()));
        item.setPickupLocation(trimToNull(dto.getPickupLocation()));
        item.setDeliveryLocation(trimToNull(dto.getDeliveryLocation()));
        item.setDeadlineTime(dto.getDeadlineTime());
        itemMapper.updateById(item);
        if (!review.reviewError()) {
            dismissPendingViolationsAfterCorrection(itemId);
        }
        if (review.reviewError()) {
            saveViolationReport(publisherId, item.getId(), dto, review, true);
        }
        return marketplaceService.getSnapshot(itemId, publisherId);
    }

    /**
     * 重新上架不是单纯状态切换：普通下架商品必须重新经过 AI 审核；
     * 已有待处理违规记录的商品必须先修改内容或由管理员放行。
     */
    @Transactional
    public void relist(Long publisherId, Long itemId) {
        Item item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在");
        }
        if (!Objects.equals(item.getPublisherId(), publisherId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能操作自己发布的商品");
        }
        if (!"OFF_SHELF".equals(item.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "只有已下架商品可以重新上架");
        }
        // ERRAND 类型：截止时间已过的跑腿单不允许重新上架
        if ("ERRAND".equals(item.getType()) && item.getDeadlineTime() != null
                && !item.getDeadlineTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "已过截止时间的跑腿单无法重新上架，请重新发布");
        }
        if (hasPendingViolation(itemId)) {
            throw new BusinessException(ResultCode.AI_VIOLATION, "该商品存在待处理违规记录，请修改内容后重试或等待管理员复核");
        }

        Category category = categoryMapper.selectById(item.getCategoryId());
        if (category == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品分类不存在");
        }
        PublishItemDTO dto = toReviewDTO(item);
        ReviewResult review = review(dto, category);
        if (review.violation()) {
            TransactionTemplate newTx = new TransactionTemplate(transactionManager);
            newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            newTx.executeWithoutResult(status -> {
                Item patch = new Item();
                patch.setId(itemId);
                patch.setAiReviewed(true);
                itemMapper.updateById(patch);
                saveViolationReport(publisherId, itemId, dto, review, false);
            });
            throw new BusinessException(ResultCode.AI_VIOLATION, review.reason());
        }

        item.setStatus("ON_SALE");
        item.setAiReviewed(!review.reviewError());
        item.setAiTags(toJson(review.tags()));
        itemMapper.updateById(item);
        if (review.reviewError()) {
            saveViolationReport(publisherId, itemId, dto, review, true);
        }
    }

    private Item buildItem(Long publisherId, Long schoolId, PublishItemDTO dto, ReviewResult review) {
        Item item = new Item();
        item.setPublisherId(publisherId);
        item.setSchoolId(schoolId);
        item.setType(dto.getType());
        item.setTitle(dto.getTitle().trim());
        item.setDescription(dto.getDescription().trim());
        item.setCategoryId(dto.getCategoryId());
        item.setPrice(normalizePrice(dto));
        item.setImages(toJson(dto.getImages()));
        item.setAiTags(toJson(review.tags()));
        item.setTradeLocation(trimToNull(dto.getTradeLocation()));
        item.setPickupLocation(trimToNull(dto.getPickupLocation()));
        item.setDeliveryLocation(trimToNull(dto.getDeliveryLocation()));
        item.setDeadlineTime(dto.getDeadlineTime());
        item.setViewCount(0);
        item.setIsDeleted(false);
        return item;
    }

    private void validateImages(List<String> images) {
        for (String image : images) {
            if (!StringUtils.hasText(image) || !image.startsWith("/uploads/items/")) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "图片地址必须来自平台上传接口");
            }
        }
    }

    private ReviewResult review(PublishItemDTO dto, Category category) {
        AiReviewService.ReviewResult result = aiReviewService.review(dto, category);
        List<String> tags = result.tags().isEmpty() ? generateTags(dto, category) : result.tags();
        return new ReviewResult(result.violation(), result.reason(), tags, result.reviewError());
    }

    private List<String> generateTags(PublishItemDTO dto, Category category) {
        Set<String> tags = new LinkedHashSet<>();
        tags.add(category.getName());
        String text = dto.getTitle() + " " + dto.getDescription();
        addKnownTags(text, tags);
        for (String token : text.split("[\\s,，。.!！?？、/\\\\()（）\\[\\]【】]+")) {
            String value = token.trim();
            if (value.length() >= 2 && value.length() <= 16 && tags.size() < 6) {
                tags.add(value);
            }
        }
        tags.add(switch (dto.getType()) {
            case "SELL" -> "出售";
            case "BUY" -> "求购";
            case "SWAP" -> "以物换物";
            case "ERRAND" -> "校园跑腿";
            default -> dto.getType();
        });
        return new ArrayList<>(tags).stream().limit(6).toList();
    }

    private void addKnownTags(String text, Set<String> tags) {
        String normalized = text.toLowerCase(Locale.ROOT);
        List<String> candidates = List.of(
                "iPad", "苹果", "小米", "华为", "耳机", "键盘", "充电宝", "教材", "高数", "考研",
                "四级", "全新", "99新", "有笔记", "台灯", "风扇", "背包", "运动鞋", "篮球", "Switch"
        );
        for (String candidate : candidates) {
            if (normalized.contains(candidate.toLowerCase(Locale.ROOT)) && tags.size() < 6) {
                tags.add(candidate);
            }
        }
    }

    private void saveViolationReport(Long userId, Long itemId, PublishItemDTO dto, ReviewResult review, boolean aiReviewError) {
        String violationType = aiReviewError ? "AI_REVIEW_ERROR" : "CONTENT_VIOLATION";
        Long existing = violationReportMapper.selectCount(new LambdaQueryWrapper<ViolationReport>()
                .eq(ViolationReport::getItemId, itemId)
                .eq(ViolationReport::getViolationType, violationType)
                .eq(ViolationReport::getStatus, "PENDING"));
        if (existing != null && existing > 0) {
            return;
        }
        ViolationReport report = new ViolationReport();
        report.setUserId(userId);
        report.setItemId(itemId);
        report.setOriginalTitle(dto.getTitle());
        report.setOriginalDescription(dto.getDescription());
        report.setViolationType(violationType);
        report.setViolationReason(review.reason());
        report.setAiTags(toJson(review.tags()));
        report.setStatus("PENDING");
        report.setAiReviewError(aiReviewError);
        violationReportMapper.insert(report);
    }

    private boolean hasPendingViolation(Long itemId) {
        return violationReportMapper.selectCount(new LambdaQueryWrapper<ViolationReport>()
                .eq(ViolationReport::getItemId, itemId)
                .eq(ViolationReport::getStatus, "PENDING")
                .eq(ViolationReport::getAiReviewError, false)) > 0;
    }

    private void dismissPendingViolationsAfterCorrection(Long itemId) {
        violationReportMapper.update(null, new LambdaUpdateWrapper<ViolationReport>()
                .eq(ViolationReport::getItemId, itemId)
                .eq(ViolationReport::getStatus, "PENDING")
                .eq(ViolationReport::getAiReviewError, false)
                .set(ViolationReport::getStatus, "DISMISSED")
                .set(ViolationReport::getHandleNote, "用户修改内容后重新通过 AI 审核")
                .set(ViolationReport::getHandledAt, LocalDateTime.now()));
    }

    private PublishItemDTO toReviewDTO(Item item) {
        PublishItemDTO dto = new PublishItemDTO();
        dto.setType(item.getType());
        dto.setTitle(item.getTitle());
        dto.setDescription(item.getDescription());
        dto.setCategoryId(item.getCategoryId());
        dto.setPrice(item.getPrice());
        dto.setTradeLocation(item.getTradeLocation());
        dto.setPickupLocation(item.getPickupLocation());
        dto.setDeliveryLocation(item.getDeliveryLocation());
        dto.setDeadlineTime(item.getDeadlineTime());
        return dto;
    }

    private BigDecimal normalizePrice(PublishItemDTO dto) {
        return "SWAP".equals(dto.getType()) ? null : dto.getPrice().setScale(2);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new BusinessException(ResultCode.SERVER_ERROR, "JSON 序列化失败");
        }
    }

    private String extensionOf(String originalFilename, String contentType) {
        String ext = "";
        if (StringUtils.hasText(originalFilename) && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        }
        if (!List.of("jpg", "jpeg", "png", "webp").contains(ext)) {
            if ("image/jpeg".equalsIgnoreCase(contentType)) ext = "jpg";
            else if ("image/png".equalsIgnoreCase(contentType)) ext = "png";
            else if ("image/webp".equalsIgnoreCase(contentType)) ext = "webp";
        }
        if (!List.of("jpg", "jpeg", "png", "webp").contains(ext)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅支持 jpg、png、webp 图片");
        }
        return "jpeg".equals(ext) ? "jpg" : ext;
    }

    private record ReviewResult(boolean violation, String reason, List<String> tags, boolean reviewError) {
    }
}
