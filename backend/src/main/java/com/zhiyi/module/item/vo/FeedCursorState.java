package com.zhiyi.module.item.vo;

/**
 * Feed 游标状态（服务端签名，客户端不透明）。
 */
public class FeedCursorState {
    /** 签发用户 ID：游标链只能由签发者本人续页（跨用户复用一律拒绝重启） */
    public long userId;
    /** 规范化筛选条件 + 排序方式的 SHA-256 摘要（前 16 hex 已够绑定语义，用完整 64） */
    public String filterHash;
    /** 签发时用户资料版本；变化即要求从首屏重启 */
    public long profileVersion;
    /** 快照上界：签发后新发布的商品（id 更大）不得进入本游标链 */
    public long snapshotMaxItemId;
    /** 快照上界：listing_revision 超出上界的编辑商品退出旧链（可消失，不得换位置重现） */
    public long snapshotMaxRevision;
    /** 排序代码：random/latest/priceAsc/priceDesc/views */
    public String sort;
    /** random 分层推荐当前层级索引 */
    public int tierIndex;
    /** keyset 边界：上一页最后一条的排序键分量 */
    public Long lastSortKey;
    public Long lastSecondaryKey;
    public Long lastItemId;
    /** 首屏估算 total（明确标记为估算，不承诺跨页精确） */
    public long estimatedTotal;
    /** 过期时间（epoch 秒） */
    public long expiresAtEpochSecond;
}
