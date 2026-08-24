package com.zhiyi.module.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.vo.ViewsSortQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ItemMapper extends BaseMapper<Item> {

    /** 当前读锁定商品行（交易/审核聚合串行点），锁定后必须重读可变字段。 */
    @Select("SELECT * FROM item WHERE id = #{id} FOR UPDATE")
    Item selectByIdForUpdate(@Param("id") Long id);

    /** NOWAIT 锁定商品行：锁繁忙立即失败（errno 3572），由调用方映射为可重试背压。 */
    @Select("SELECT * FROM item WHERE id = #{id} FOR UPDATE NOWAIT")
    Item selectByIdForUpdateNowait(@Param("id") Long id);

    /** listing_revision 全局序列推进（事务内行锁持有到提交，天然串行）。 */
    @Update("UPDATE feed_sequence SET current_value = current_value + 1 WHERE id = 0")
    int bumpListingRevision();

    /** 读取当前序列值（必须在同一事务内先调用 bumpListingRevision）。 */
    @Select("SELECT current_value FROM feed_sequence WHERE id = 0")
    long currentListingRevision();

    /** 游标签发时的商品 id 快照上界（排除签发后新发布的商品）。 */
    @Select("SELECT COALESCE(MAX(id), 0) FROM item")
    long maxItemId();

    /**
     * 条件软删除：仅 ON_SALE/OFF_SHELF 且归属本人的商品可删
     * （RESERVED/SOLD 及并发迁移中的状态一律 0 行拒绝，参数绑定）。
     * 自定义 SQL 绕开 @TableLogic 拦截以携带状态条件。
     */
    @Update("UPDATE item SET is_deleted = 1 "
            + "WHERE id = #{itemId} AND publisher_id = #{publisherId} "
            + "AND status IN ('ON_SALE', 'OFF_SHELF')")
    int softDeleteEditable(@Param("itemId") Long itemId, @Param("publisherId") Long publisherId);

    /**
     * 浏览量排序 keyset 分页（join item_view_stat；view_count 单调递增保证
     * DESC + keyset 无重复，只可能因计数增长漏过个别条目——明确的近似语义）。
     */
    @Select("""
            <script>
            SELECT i.* FROM item i
            JOIN item_view_stat vs ON vs.item_id = i.id
             WHERE i.school_id = #{q.schoolId}
               AND i.status = 'ON_SALE' AND i.moderation_status = 'PASSED' AND i.is_deleted = 0
               AND i.id &lt;= #{q.snapshotMaxItemId}
               AND i.listing_revision &lt;= #{q.snapshotMaxRevision}
               <if test="q.keyword != null and q.keyword != ''">
                 AND (i.title LIKE CONCAT('%', #{q.keyword}, '%')
                      OR i.description LIKE CONCAT('%', #{q.keyword}, '%')
                      OR EXISTS (SELECT 1 FROM item_tag ik JOIN tag tk ON tk.id = ik.tag_id
                                 WHERE ik.item_id = i.id AND tk.name LIKE CONCAT('%', #{q.keyword}, '%')))
               </if>
               <if test="q.categoryId != null">AND i.category_id = #{q.categoryId}</if>
               <if test="q.minPrice != null">AND i.price &gt;= #{q.minPrice}</if>
               <if test="q.maxPrice != null">AND i.price &lt;= #{q.maxPrice}</if>
               <if test="q.type != null">AND i.type = #{q.type}</if>
               <if test="q.tags != null and q.tags.size() > 0">
                 AND EXISTS (SELECT 1 FROM item_tag it JOIN tag t ON t.id = it.tag_id
                             WHERE it.item_id = i.id AND t.normalized_name IN
                             <foreach collection="q.tags" item="tg" open="(" separator="," close=")">#{tg}</foreach>)
               </if>
               <if test="q.cursorViewCount != null">
                 AND (vs.view_count &lt; #{q.cursorViewCount}
                      OR (vs.view_count = #{q.cursorViewCount} AND i.id &lt; #{q.cursorItemId}))
               </if>
             ORDER BY vs.view_count DESC, i.id DESC
             LIMIT #{q.limit}
            </script>
            """)
    List<Item> selectViewsSortedPage(@Param("q") ViewsSortQuery query);
}
