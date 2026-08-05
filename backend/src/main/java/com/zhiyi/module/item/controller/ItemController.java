package com.zhiyi.module.item.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zhiyi.common.Result;
import com.zhiyi.module.admin.service.AdminLineageService;
import com.zhiyi.module.admin.vo.ItemLineageVO;
import com.zhiyi.module.item.dto.PublishItemDTO;
import com.zhiyi.module.item.service.ItemPublishService;
import com.zhiyi.module.item.service.MarketplaceService;
import com.zhiyi.module.item.vo.AiTagTrendVO;
import com.zhiyi.module.item.vo.FavoriteToggleVO;
import com.zhiyi.module.item.vo.ItemCardVO;
import com.zhiyi.module.item.vo.UploadImageVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/item")
@RequiredArgsConstructor
public class ItemController {

    private final MarketplaceService marketplaceService;
    private final ItemPublishService itemPublishService;
    private final AdminLineageService lineageService;

    @PostMapping("/upload-image")
    public Result<UploadImageVO> uploadImage(@RequestParam("file") MultipartFile file) {
        return Result.ok(itemPublishService.uploadImage(file));
    }

    @PostMapping("/publish")
    public Result<ItemCardVO> publish(@RequestAttribute("userId") Long userId,
                                      @Valid @RequestBody PublishItemDTO dto) {
        return Result.ok("发布成功", itemPublishService.publish(userId, dto));
    }

    @GetMapping("/my-items/{id}")
    public Result<ItemCardVO> ownItem(@RequestAttribute("userId") Long userId,
                                      @PathVariable Long id) {
        return Result.ok(marketplaceService.getOwnItem(userId, id));
    }

    @PutMapping("/{id}")
    public Result<ItemCardVO> update(@RequestAttribute("userId") Long userId,
                                     @PathVariable Long id,
                                     @Valid @RequestBody PublishItemDTO dto) {
        return Result.ok("修改成功", itemPublishService.update(userId, id, dto));
    }

    @GetMapping("/list")
    public Result<IPage<ItemCardVO>> list(@RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) Long categoryId,
                                          @RequestParam(required = false) BigDecimal minPrice,
                                          @RequestParam(required = false) BigDecimal maxPrice,
                                          @RequestParam(required = false) String type,
                                          @RequestParam(required = false) String tag,
                                          @RequestParam(defaultValue = "random") String sort,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "12") int size,
                                          @RequestAttribute("userId") Long userId) {
        return Result.ok(marketplaceService.listOnSaleItems(
                keyword, categoryId, minPrice, maxPrice, sort, type, tag, page, size, userId));
    }

    @GetMapping("/search")
    public Result<IPage<ItemCardVO>> search(@RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) Long categoryId,
                                            @RequestParam(required = false) BigDecimal minPrice,
                                            @RequestParam(required = false) BigDecimal maxPrice,
                                            @RequestParam(required = false) String type,
                                            @RequestParam(required = false) String tag,
                                            @RequestParam(defaultValue = "latest") String sort,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "12") int size,
                                            @RequestAttribute("userId") Long userId) {
        return Result.ok(marketplaceService.listOnSaleItems(
                keyword, categoryId, minPrice, maxPrice, sort, type, tag, page, size, userId));
    }

    @GetMapping("/tags")
    public Result<List<Map<String, Object>>> allTags(@RequestAttribute("userId") Long userId) {
        return Result.ok(marketplaceService.getAllTags(userId));
    }

    @GetMapping("/ranking")
    public Result<List<ItemCardVO>> ranking(@RequestParam(defaultValue = "10") int limit,
                                            @RequestAttribute("userId") Long userId) {
        return Result.ok(marketplaceService.ranking(limit, userId));
    }

    @GetMapping("/swap-matches")
    public Result<List<ItemCardVO>> swapMatches(@RequestAttribute("userId") Long userId) {
        return Result.ok(marketplaceService.listSwapMatches(userId));
    }

    @GetMapping("/errands")
    public Result<List<ItemCardVO>> errands(@RequestAttribute("userId") Long userId) {
        return Result.ok(marketplaceService.listErrands(userId));
    }

    @GetMapping("/ranking/tags")
    public Result<List<AiTagTrendVO>> trendingAiTags(@RequestParam(defaultValue = "10") int limit,
                                                     @RequestAttribute("userId") Long userId) {
        return Result.ok(marketplaceService.trendingAiTags(limit, userId));
    }

    @GetMapping("/{id}")
    public Result<ItemCardVO> detail(@PathVariable Long id,
                                     @RequestAttribute("userId") Long userId) {
        return Result.ok(marketplaceService.getDetail(id, userId));
    }

    @GetMapping("/{id}/lineage")
    public Result<ItemLineageVO> lineage(@PathVariable Long id,
                                         @RequestAttribute("userId") Long userId) {
        marketplaceService.requireVisibleItem(userId, id);
        return Result.ok(lineageService.getLineage(id));
    }

    @PostMapping("/{id}/favorite")
    public Result<FavoriteToggleVO> favorite(@RequestAttribute("userId") Long userId,
                                             @PathVariable Long id) {
        return Result.ok(marketplaceService.toggleFavorite(userId, id));
    }

    @GetMapping("/my-favorites")
    public Result<IPage<ItemCardVO>> myFavorites(@RequestAttribute("userId") Long userId,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "12") int size) {
        return Result.ok(marketplaceService.listMyFavorites(userId, page, size));
    }

    @GetMapping("/my-items")
    public Result<IPage<ItemCardVO>> myItems(@RequestAttribute("userId") Long userId,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "10") int size) {
        return Result.ok(marketplaceService.listMyItems(userId, status, page, size));
    }

    @PutMapping("/{id}/off-shelf")
    public Result<Void> offShelf(@RequestAttribute("userId") Long userId,
                                 @PathVariable Long id) {
        marketplaceService.offShelf(userId, id);
        return Result.ok("已下架", null);
    }

    @PutMapping("/{id}/relist")
    public Result<Void> relist(@RequestAttribute("userId") Long userId,
                               @PathVariable Long id) {
        itemPublishService.relist(userId, id);
        return Result.ok("AI 审核通过，已重新上架", null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@RequestAttribute("userId") Long userId,
                               @PathVariable Long id) {
        marketplaceService.deleteOwnItem(userId, id);
        return Result.ok("已删除", null);
    }

}
