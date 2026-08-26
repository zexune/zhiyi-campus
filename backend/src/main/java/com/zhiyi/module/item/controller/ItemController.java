package com.zhiyi.module.item.controller;

import com.zhiyi.common.ApiSuccess;
import com.zhiyi.common.PageResponse;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.annotation.BusinessErrors;
import com.zhiyi.common.enums.ModerationStatus;
import com.zhiyi.module.admin.service.AdminLineageService;
import com.zhiyi.module.admin.service.ViolationAppealService;
import com.zhiyi.module.admin.dto.SubmitAppealDTO;
import com.zhiyi.module.admin.vo.AppealVO;
import com.zhiyi.module.admin.vo.ItemLineageVO;
import com.zhiyi.module.item.dto.PublishItemDTO;
import com.zhiyi.module.item.dto.ReportItemDTO;
import com.zhiyi.module.item.service.ItemPublishService;
import com.zhiyi.module.item.service.MarketplaceService;
import com.zhiyi.module.item.vo.FavoriteToggleVO;
import com.zhiyi.module.item.vo.ItemCardVO;
import com.zhiyi.module.item.vo.ItemDetailResponse;
import com.zhiyi.module.item.vo.ItemSummaryResponse;
import com.zhiyi.module.item.vo.MarketplaceFeedVO;
import com.zhiyi.module.item.vo.TagTrendVO;
import com.zhiyi.module.item.vo.TagGroupVO;
import com.zhiyi.module.item.vo.UploadImageVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/item")
@RequiredArgsConstructor
public class ItemController {

    private final MarketplaceService marketplaceService;
    private final ItemPublishService itemPublishService;
    private final AdminLineageService lineageService;
    private final ViolationAppealService appealService;

    /**
     * 图片上传（multipart 契约）：显式声明 consumes 使 springdoc 生成
     * required requestBody + multipart/form-data + file(binary)；@NotNull
     * 同时驱动 springdoc 生成 requestBody.required=true（@RequestPart 的
     * required 不被 springdoc 识别）。缺 file/空文件属于 400；Servlet 限额
     * 拒绝为 413；Content-Type 不匹配为 415，均返回统一 ApiFailure。
     */
    @PostMapping(value = "/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @BusinessErrors(ResultCode.SERVER_ERROR)
    public ApiSuccess<UploadImageVO> uploadImage(@RequestPart("file") @NotNull MultipartFile file) {
        return ApiSuccess.ok(itemPublishService.uploadImage(file));
    }

    /**
     * 标签建议：按标题与分类生成候选标签，仅供发布页选择，不落库；
     * 分类不存在时 404。
     */
    @PostMapping("/tag-suggestions")
    @BusinessErrors(ResultCode.NOT_FOUND)
    public ApiSuccess<List<String>> tagSuggestions(@Valid @RequestBody TagSuggestionRequest request) {
        return ApiSuccess.ok(itemPublishService.suggestTags(request.title(), request.categoryId()));
    }

    /**
     * 内部 DTO
     */
    public record TagSuggestionRequest(
            @jakarta.validation.constraints.NotBlank(message = "标题不能为空")
            @jakarta.validation.constraints.Size(max = 50, message = "标题最长50字") String title,
            Long categoryId) {
    }

    @PostMapping("/publish")
    @BusinessErrors({ResultCode.USER_NOT_FOUND, ResultCode.NOT_FOUND, ResultCode.CONFLICT,
            ResultCode.FORBIDDEN})
    public ApiSuccess<ItemCardVO> publish(@RequestAttribute("userId") Long userId,
                                          @Valid @RequestBody PublishItemDTO dto) {
        return ApiSuccess.ok("发布成功", itemPublishService.publish(userId, dto));
    }

    @GetMapping("/my-items/{id}")
    @BusinessErrors({ResultCode.NOT_FOUND, ResultCode.FORBIDDEN})
    public ApiSuccess<ItemDetailResponse> ownItem(@RequestAttribute("userId") Long userId,
                                                  @PathVariable Long id) {
        return ApiSuccess.ok(marketplaceService.getOwnItem(userId, id));
    }

    @PutMapping("/{id}")
    @BusinessErrors({ResultCode.USER_NOT_FOUND, ResultCode.NOT_FOUND, ResultCode.CONFLICT,
            ResultCode.FORBIDDEN})
    public ApiSuccess<ItemCardVO> update(@RequestAttribute("userId") Long userId,
                                         @PathVariable Long id,
                                         @Valid @RequestBody PublishItemDTO dto) {
        return ApiSuccess.ok("修改成功", itemPublishService.update(userId, id, dto));
    }

    /**
     * 大厅 Feed（签名游标协议）：cursor 为空取首屏；hasMore + nextCursor 翻页。
     * total 为首屏估算值（estimatedTotal），不承诺跨页精确。
     */
    @GetMapping("/list")
    @BusinessErrors({ResultCode.FEED_CURSOR_INVALID, ResultCode.USER_NOT_FOUND})
    public ApiSuccess<MarketplaceFeedVO> list(@RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) Long categoryId,
                                              @RequestParam(required = false) BigDecimal minPrice,
                                              @RequestParam(required = false) BigDecimal maxPrice,
                                              @RequestParam(required = false) String type,
                                              @RequestParam(required = false) List<String> tag,
                                              @RequestParam(defaultValue = "random") String sort,
                                              @RequestParam(required = false) String cursor,
                                              @RequestParam(defaultValue = "12") int size,
                                              @RequestAttribute("userId") Long userId) {
        return ApiSuccess.ok(marketplaceService.listFeed(
                keyword, categoryId, minPrice, maxPrice, sort, type, tag, cursor, size, userId));
    }

    @GetMapping("/search")
    @BusinessErrors({ResultCode.FEED_CURSOR_INVALID, ResultCode.USER_NOT_FOUND})
    public ApiSuccess<MarketplaceFeedVO> search(@RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) Long categoryId,
                                                @RequestParam(required = false) BigDecimal minPrice,
                                                @RequestParam(required = false) BigDecimal maxPrice,
                                                @RequestParam(required = false) String type,
                                                @RequestParam(required = false) List<String> tag,
                                                @RequestParam(defaultValue = "latest") String sort,
                                                @RequestParam(required = false) String cursor,
                                                @RequestParam(defaultValue = "12") int size,
                                                @RequestAttribute("userId") Long userId) {
        return ApiSuccess.ok(marketplaceService.listFeed(
                keyword, categoryId, minPrice, maxPrice, sort, type, tag, cursor, size, userId));
    }

    @GetMapping("/tags")
    @BusinessErrors(ResultCode.USER_NOT_FOUND)
    public ApiSuccess<List<TagGroupVO>> allTags(@RequestAttribute("userId") Long userId) {
        return ApiSuccess.ok(marketplaceService.getAllTags(userId));
    }

    @GetMapping("/ranking")
    @BusinessErrors(ResultCode.USER_NOT_FOUND)
    public ApiSuccess<List<ItemSummaryResponse>> ranking(@RequestParam(defaultValue = "10") int limit,
                                                @RequestAttribute("userId") Long userId) {
        return ApiSuccess.ok(marketplaceService.ranking(limit, userId));
    }

    @GetMapping("/swap-matches")
    @BusinessErrors(ResultCode.USER_NOT_FOUND)
    public ApiSuccess<List<ItemSummaryResponse>> swapMatches(@RequestAttribute("userId") Long userId) {
        return ApiSuccess.ok(marketplaceService.listSwapMatches(userId));
    }

    @GetMapping("/errands")
    @BusinessErrors(ResultCode.USER_NOT_FOUND)
    public ApiSuccess<List<ItemSummaryResponse>> errands(@RequestAttribute("userId") Long userId) {
        return ApiSuccess.ok(marketplaceService.listErrands(userId));
    }

    @GetMapping("/ranking/tags")
    @BusinessErrors(ResultCode.USER_NOT_FOUND)
    public ApiSuccess<List<TagTrendVO>> trendingTags(@RequestParam(defaultValue = "10") int limit,
                                                     @RequestAttribute("userId") Long userId) {
        return ApiSuccess.ok(marketplaceService.trendingTags(limit, userId));
    }

    /** 跨校浏览同校隔离：非同校用户访问详情返回 403（SchoolScopeGuard）。 */
    @GetMapping("/{id}")
    @BusinessErrors({ResultCode.USER_NOT_FOUND, ResultCode.NOT_FOUND, ResultCode.FORBIDDEN})
    public ApiSuccess<ItemDetailResponse> detail(@PathVariable Long id,
                                                 @RequestAttribute("userId") Long userId) {
        return ApiSuccess.ok(marketplaceService.getDetail(id, userId));
    }

    @GetMapping("/{id}/lineage")
    @BusinessErrors({ResultCode.USER_NOT_FOUND, ResultCode.NOT_FOUND, ResultCode.FORBIDDEN})
    public ApiSuccess<ItemLineageVO> lineage(@PathVariable Long id,
                                             @RequestAttribute("userId") Long userId) {
        marketplaceService.requireVisibleItem(userId, id);
        return ApiSuccess.ok(lineageService.getLineage(id));
    }

    @PostMapping("/{id}/favorite")
    @BusinessErrors({ResultCode.USER_NOT_FOUND, ResultCode.NOT_FOUND,
            ResultCode.ITEM_NOT_ON_SALE, ResultCode.FORBIDDEN})
    public ApiSuccess<FavoriteToggleVO> favorite(@RequestAttribute("userId") Long userId,
                                                 @PathVariable Long id) {
        return ApiSuccess.ok(marketplaceService.toggleFavorite(userId, id));
    }

    @GetMapping("/my-favorites")
    @BusinessErrors(ResultCode.USER_NOT_FOUND)
    public ApiSuccess<PageResponse<ItemCardVO>> myFavorites(@RequestAttribute("userId") Long userId,
                                                            @RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "12") int size) {
        return ApiSuccess.ok(PageResponse.from(marketplaceService.listMyFavorites(userId, page, size)));
    }

    @GetMapping("/my-items")
    @BusinessErrors
    public ApiSuccess<PageResponse<ItemDetailResponse>> myItems(@RequestAttribute("userId") Long userId,
                                                                @RequestParam(required = false) String status,
                                                                @RequestParam(defaultValue = "1") int page,
                                                                @RequestParam(defaultValue = "10") int size) {
        return ApiSuccess.ok(PageResponse.from(marketplaceService.listMyItems(userId, status, page, size)));
    }

    @PutMapping("/{id}/off-shelf")
    @BusinessErrors({ResultCode.NOT_FOUND, ResultCode.CONFLICT, ResultCode.FORBIDDEN})
    public ApiSuccess<Void> offShelf(@RequestAttribute("userId") Long userId,
                                     @PathVariable Long id) {
        marketplaceService.offShelf(userId, id);
        return ApiSuccess.ok("已下架", null);
    }

    @PutMapping("/{id}/relist")
    @BusinessErrors({ResultCode.USER_NOT_FOUND, ResultCode.NOT_FOUND, ResultCode.CONFLICT,
            ResultCode.FORBIDDEN})
    public ApiSuccess<ItemCardVO> relist(@RequestAttribute("userId") Long userId,
                                         @PathVariable Long id) {
        ItemCardVO item = itemPublishService.relist(userId, id);
        String message = ModerationStatus.PENDING.code().equals(item.getModerationStatus())
                ? "检测到风险内容，已提交管理员审核"
                : "已重新上架";
        return ApiSuccess.ok(message, item);
    }

    /** 举报：商品不可见（403）与重复举报（409）都是显式契约。 */
    @PostMapping("/{id}/reports")
    @BusinessErrors({ResultCode.USER_NOT_FOUND, ResultCode.NOT_FOUND,
            ResultCode.FORBIDDEN, ResultCode.CONFLICT})
    public ApiSuccess<Void> report(@RequestAttribute("userId") Long userId,
                                   @PathVariable Long id,
                                   @Valid @RequestBody ReportItemDTO dto) {
        itemPublishService.report(userId, id, dto);
        return ApiSuccess.ok("举报已提交，管理员会尽快处理", null);
    }

    @PostMapping("/{id}/appeals")
    @BusinessErrors({ResultCode.NOT_FOUND, ResultCode.CONFLICT})
    public ApiSuccess<AppealVO> appeal(@RequestAttribute("userId") Long userId,
                                       @PathVariable Long id,
                                       @Valid @RequestBody SubmitAppealDTO dto) {
        return ApiSuccess.ok("申诉已提交", appealService.submitLatestForItem(userId, id, dto));
    }

    @DeleteMapping("/{id}")
    @BusinessErrors({ResultCode.NOT_FOUND, ResultCode.CONFLICT, ResultCode.FORBIDDEN})
    public ApiSuccess<Void> delete(@RequestAttribute("userId") Long userId,
                                   @PathVariable Long id) {
        marketplaceService.deleteOwnItem(userId, id);
        return ApiSuccess.ok("已删除", null);
    }

}
