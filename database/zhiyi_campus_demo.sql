-- This file is UTF-8. Set the connection charset before any non-ASCII SQL.
-- ============================================================
-- 🎓 智易校园 - 演示数据脚本（可选，开发展示用）
--
-- 前置：先执行 database/zhiyi_campus_init.sql（同目录）。本脚本依赖其中的学校字典主键：
--       1=上海大学、2=东华大学、3=平台系统学校、4=华东师范大学（本脚本追加）。
-- 用法：mysql -uroot --default-character-set=utf8mb4 < database/zhiyi_campus_demo.sql
--       （Docker 容器内：mysql -uroot < /repo/database/zhiyi_campus_demo.sql）
-- 说明：
-- - 只做 INSERT，不建库不建表；重复执行会因唯一键冲突失败，
--   重置演示数据请先重跑 database/zhiyi_campus_init.sql 再执行本脚本；
-- - 时间基于 NOW() 相对生成，重复灌库后演示窗口（180/30 天）恒有效；
-- - 演示用户密码与 admin 相同：123456（学号见各 INSERT）；
-- - 一致性保证：exp_log 合计 = sys_user.exp（等级按 LevelRule 曲线逐条结算），
--   wallet_log 按用户时间正序 balance_after 连续且合计 = wallet_balance；
-- - 同买家-卖家对完成单按 PairDecayRule 衰减（第1次1.0 / 2-5次0.5 / 6次起0.2）；
-- - user_reputation_metric 为固定大小汇总行，sample_count 覆盖历史明细，
--   chat_response_sample 仅含可见会话对应的部分明细（离线回填语义）；
-- - 集成测试（pom testResources）只加载 database/zhiyi_campus_init.sql，本脚本不影响测试。
-- ============================================================
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

USE zhiyi_campus;

-- 1.1 补充学校（注册页学校列表更饱满）
INSERT INTO school (name, code, email_domain, status) VALUES
('华东师范大学', 'ECNU', '@ecnu.edu.cn', 'ACTIVE');

-- 1.2 演示用户（密码均为 123456；学号见 student_id）
INSERT INTO sys_user (student_id, password, nickname, school_id, school_email, campus, college, grade, dormitory, role, status, level, exp, wallet_balance, security_question, security_answer, is_system, ban_until_time)
VALUES
('23110001', '$2a$10$or0s3jeC85J07b8HcY9wfOJDE0gegLcyYkjFLn0yr.BE8koej.A1K', '小萌主', 1, 'xiaomeng@shu.edu.cn', '宝山校区', '通信与信息工程学院', '2023', '新世纪大学生村', 'USER', 'ACTIVE', 3, 255, 1605.00, '你母亲的生日是？', '$2a$10$or0s3jeC85J07b8HcY9wfOJDE0gegLcyYkjFLn0yr.BE8koej.A1K', 0, NULL),
('23110002', '$2a$10$or0s3jeC85J07b8HcY9wfOJDE0gegLcyYkjFLn0yr.BE8koej.A1K', '可欣不熬夜', 1, 'kexin@shu.edu.cn', '宝山校区', '管理学院', '2024', '新世纪大学生村', 'USER', 'ACTIVE', 2, 90, 75.00, '你最喜欢的城市是？', '$2a$10$or0s3jeC85J07b8HcY9wfOJDE0gegLcyYkjFLn0yr.BE8koej.A1K', 0, NULL),
('23110003', '$2a$10$or0s3jeC85J07b8HcY9wfOJDE0gegLcyYkjFLn0yr.BE8koej.A1K', '浩然同学', 1, NULL, NULL, NULL, NULL, NULL, 'USER', 'ACTIVE', 1, 25, 102.00, '你的第一只宠物叫什么名字？', '$2a$10$or0s3jeC85J07b8HcY9wfOJDE0gegLcyYkjFLn0yr.BE8koej.A1K', 0, NULL),
('23110004', '$2a$10$or0s3jeC85J07b8HcY9wfOJDE0gegLcyYkjFLn0yr.BE8koej.A1K', '婷婷爱囤货', 1, 'tingting@shu.edu.cn', '宝山校区', '社会学院', '2023', '东区9号楼', 'USER', 'ACTIVE', 2, 96, 551.00, '你最喜欢的城市是？', '$2a$10$or0s3jeC85J07b8HcY9wfOJDE0gegLcyYkjFLn0yr.BE8koej.A1K', 0, NULL),
('23110005', '$2a$10$or0s3jeC85J07b8HcY9wfOJDE0gegLcyYkjFLn0yr.BE8koej.A1K', '老周杂货铺', 1, NULL, NULL, NULL, NULL, NULL, 'USER', 'ACTIVE', 1, 10, 50.00, '你高中班主任的名字是？', '$2a$10$or0s3jeC85J07b8HcY9wfOJDE0gegLcyYkjFLn0yr.BE8koej.A1K', 0, NULL),
('23110006', '$2a$10$or0s3jeC85J07b8HcY9wfOJDE0gegLcyYkjFLn0yr.BE8koej.A1K', '一鸣游戏搬家', 1, NULL, '宝山校区', '计算机工程与科学学院', '2022', '东区9号楼', 'USER', 'ACTIVE', 3, 228, 600.00, '你最喜欢的城市是？', '$2a$10$or0s3jeC85J07b8HcY9wfOJDE0gegLcyYkjFLn0yr.BE8koej.A1K', 0, NULL),
('23110007', '$2a$10$or0s3jeC85J07b8HcY9wfOJDE0gegLcyYkjFLn0yr.BE8koej.A1K', '小满手工铺', 1, NULL, '宝山校区', '美术学院', '2024', '东区11号楼', 'USER', 'ACTIVE', 2, 75, 50.00, '你母亲的生日是？', '$2a$10$or0s3jeC85J07b8HcY9wfOJDE0gegLcyYkjFLn0yr.BE8koej.A1K', 0, NULL),
('23110008', '$2a$10$or0s3jeC85J07b8HcY9wfOJDE0gegLcyYkjFLn0yr.BE8koej.A1K', '悦宁爱英语', 1, NULL, '宝山校区', '外国语学院', '2023', '新世纪大学生村', 'USER', 'ACTIVE', 2, 100, 129.00, '你最喜欢的城市是？', '$2a$10$or0s3jeC85J07b8HcY9wfOJDE0gegLcyYkjFLn0yr.BE8koej.A1K', 0, NULL),
('23120001', '$2a$10$or0s3jeC85J07b8HcY9wfOJDE0gegLcyYkjFLn0yr.BE8koej.A1K', '雨桐在华师', 4, NULL, '闵行校区', '服装与艺术设计学院', '2023', '学生公寓5栋', 'USER', 'ACTIVE', 2, 95, 136.00, '你母亲的生日是？', '$2a$10$or0s3jeC85J07b8HcY9wfOJDE0gegLcyYkjFLn0yr.BE8koej.A1K', 0, NULL),
('23120002', '$2a$10$or0s3jeC85J07b8HcY9wfOJDE0gegLcyYkjFLn0yr.BE8koej.A1K', '宇飞同学', 4, NULL, '闵行校区', '信息科学与技术学院', '2024', '学生公寓5栋', 'USER', 'ACTIVE', 2, 95, 64.00, '你最喜欢的城市是？', '$2a$10$or0s3jeC85J07b8HcY9wfOJDE0gegLcyYkjFLn0yr.BE8koej.A1K', 0, NULL),
('23110009', '$2a$10$or0s3jeC85J07b8HcY9wfOJDE0gegLcyYkjFLn0yr.BE8koej.A1K', '楠楠不打烊', 1, NULL, '宝山校区', '生命科学学院', '2022', '东区11号楼', 'USER', 'BANNED_TEMP', 1, 20, 100.00, '你母亲的生日是？', '$2a$10$or0s3jeC85J07b8HcY9wfOJDE0gegLcyYkjFLn0yr.BE8koej.A1K', 0, NOW() + INTERVAL 7 DAY);

-- 1.3 商品（images 为空数组走前端占位图；listing_revision 按发布时间递增）
INSERT INTO item (id, publisher_id, school_id, type, title, description, category_id, price, images, moderation_status, trade_location, pickup_location, delivery_location, status, feed_key, listing_revision, publisher_campus_key, publisher_dormitory_key, is_deleted, created_at)
VALUES
(1, 3, 1, 'SELL', 'iPhone 12 128G 95新无拆修', '自用一年半，一直贴膜带壳，屏幕无划痕无拆修，电池效率 89%。配件：原装充电头 + 新买的车载壳，可小刀。毕业急出，宝山校区面交优先。', 1, 1299.00, '[]', 'PASSED', '新世纪大学生村东门', NULL, NULL, 'SOLD', 6180339887498952, 6, '宝山校区', '新世纪大学生村', 0, NOW() - INTERVAL 30 DAY - INTERVAL 8 HOUR),
(2, 3, 1, 'SWAP', '雅马哈F310民谣吉他 换头戴耳机', '民谣吉他 F310，买来吃灰一年，音准没问题，弦是换过的。想换一副头戴式耳机（Sony/ Bose 均可），私聊看实物。', 5, NULL, '[]', 'PASSED', '新世纪大学生村东门', NULL, NULL, 'ON_SALE', 12360679774997903, 30, '宝山校区', '新世纪大学生村', 0, NOW() - INTERVAL 2 DAY - INTERVAL 8 HOUR),
(3, 3, 1, 'SELL', '小米LED台灯 白色', '小米 LED 台灯，亮度无极调节，灯罩有个小掉漆（图 3），不影响使用。毕业回血。', 4, 79.00, '[]', 'PASSED', '新世纪大学生村东门', NULL, NULL, 'SOLD', 18541019662496854, 23, '宝山校区', '新世纪大学生村', 0, NOW() - INTERVAL 6 DAY - INTERVAL 8 HOUR),
(4, 3, 1, 'SELL', 'Kindle Paperwhite 4 8G', 'Kindle PW4 8G，原装保护套开裂了一角但能扣紧（图 2），屏幕完美。可诚意刀。', 1, 129.00, '[]', 'PASSED', '宝山校区图书馆一楼', NULL, NULL, 'ON_SALE', 24721359549995805, 21, '宝山校区', '新世纪大学生村', 0, NOW() - INTERVAL 7 DAY - INTERVAL 8 HOUR),
(5, 3, 1, 'SELL', 'iPad Air 5 蓝色 64G 带笔尖膜', 'iPad Air 5 蓝色 64G，考试周刚过出。9.8 成新，贴了类纸膜，附赠两支笔尖膜。已备份退出 ID。', 1, 899.00, '[]', 'PASSED', '新世纪大学生村东门', NULL, NULL, 'RESERVED', 30901699437494756, 27, '宝山校区', '新世纪大学生村', 0, NOW() - INTERVAL 3 DAY - INTERVAL 8 HOUR),
(6, 3, 1, 'SELL', '帆布单肩包 米白', '米白色帆布包，背了三次，太淑女了不适合我，原价 89。', 3, 35.00, '[]', 'PASSED', '新世纪大学生村东门', NULL, NULL, 'ON_SALE', 37082039324993707, 13, '宝山校区', '新世纪大学生村', 0, NOW() - INTERVAL 12 DAY - INTERVAL 8 HOUR),
(7, 3, 1, 'SELL', '实木落地衣架', '实木落地衣架，承重很好，搬家带不走。西区自提。', 4, 29.00, '[]', 'PASSED', '西区宿舍楼下自提', NULL, NULL, 'SOLD', 43262379212492658, 22, '宝山校区', '新世纪大学生村', 0, NOW() - INTERVAL 7 DAY - INTERVAL 8 HOUR),
(8, 3, 1, 'SELL', '小熊电煮锅 1.2L', '小熊电煮锅 1.2L，煮面神器，内胆无划痕，毕业跳蚤价。', 4, 59.00, '[]', 'PASSED', '新世纪大学生村东门', NULL, NULL, 'SOLD', 49442719099991609, 3, '宝山校区', '新世纪大学生村', 0, NOW() - INTERVAL 62 DAY - INTERVAL 8 HOUR),
(9, 3, 1, 'SELL', '床上折叠小桌子 可升降', '床上折叠桌，可升降可折叠，期末背书神器。桌面有一处笔印（图 2）。', 4, 39.00, '[]', 'PASSED', '新世纪大学生村东门', NULL, NULL, 'SOLD', 55623058987490560, 2, '宝山校区', '新世纪大学生村', 0, NOW() - INTERVAL 92 DAY - INTERVAL 8 HOUR),
(10, 4, 1, 'SELL', '考研数学一二三全套（张宇）', '张宇全套（基础30讲+强化36讲+1000题），23 考研用的，写了前三章铅笔笔记，可擦。', 2, 45.00, '[]', 'PASSED', '宝山校区图书馆一楼', NULL, NULL, 'SOLD', 61803398874989511, 11, '宝山校区', '新世纪大学生村', 0, NOW() - INTERVAL 16 DAY - INTERVAL 8 HOUR),
(11, 4, 1, 'SELL', '考研英语一真题黄皮书', '考研英语一黄皮书真题卷，基础加强版，95 新无笔记。', 2, 25.00, '[]', 'PASSED', '宝山校区图书馆一楼', NULL, NULL, 'ON_SALE', 67983738762488462, 19, '宝山校区', '新世纪大学生村', 0, NOW() - INTERVAL 8 DAY - INTERVAL 8 HOUR),
(12, 4, 1, 'SELL', '六级词汇闪过', '六级词汇闪过，乱序版，背到 E 开头弃考了，几乎全新。', 2, 12.00, '[]', 'PASSED', '宝山校区图书馆一楼', NULL, NULL, 'ON_SALE', 74164078649987413, 24, '宝山校区', '新世纪大学生村', 0, NOW() - INTERVAL 6 DAY - INTERVAL 8 HOUR),
(13, 4, 1, 'SELL', '笔记本铝合金支架', '铝合金笔记本支架，四档高度，承重稳，送防滑硅胶垫。', 7, 35.00, '[]', 'PASSED', '宝山校区图书馆一楼', NULL, NULL, 'ON_SALE', 80344418537486364, 9, '宝山校区', '新世纪大学生村', 0, NOW() - INTERVAL 20 DAY - INTERVAL 8 HOUR),
(14, 5, 1, 'BUY', '求购二手通勤自行车 150以下', '求购一辆通勤自行车，150 以内，刹车灵就行，东区自提。', 1, 150.00, '[]', 'PASSED', '东区宿舍', NULL, NULL, 'ON_SALE', 86524758424985315, 32, NULL, NULL, 0, NOW() - INTERVAL 1 DAY - INTERVAL 8 HOUR),
(15, 6, 1, 'BUY', '求购Switch游戏卡带（塞尔达/马里奥）', '求购 Switch 卡带，塞尔达旷野之息 / 马里奥赛车 8，价格好商量，仅当面交易验货。', 5, 100.00, '[]', 'PASSED', '东区9号楼', NULL, NULL, 'ON_SALE', 92705098312484266, 26, '宝山校区', '东区9号楼', 0, NOW() - INTERVAL 5 DAY - INTERVAL 8 HOUR),
(16, 6, 1, 'SELL', '多肉植物组合盆 带盆出', '宿舍养多肉，出组合盆：观音莲+黄丽+白牡丹，带陶瓷盆和土，东区面交。', 6, 15.00, '[]', 'PASSED', '东区9号楼', NULL, NULL, 'ON_SALE', 98885438199983217, 17, '宝山校区', '东区9号楼', 0, NOW() - INTERVAL 9 DAY - INTERVAL 8 HOUR),
(17, 7, 1, 'SELL', '代写作业代做PPT 价格好说', '代写各科作业，代做 PPT 排版，价格好说，速度快质量高，可看样例。', 7, 50.00, '[]', 'REJECTED', NULL, NULL, NULL, 'OFF_SHELF', 105065778087482168, 1, NULL, NULL, 0, NOW() - INTERVAL 95 DAY - INTERVAL 8 HOUR),
(18, 7, 1, 'SELL', '火影忍者漫画全套1-72', '火影漫画全套 1-72 卷，台版，书页无折痕，整套打包 120 不拆。', 5, 120.00, '[]', 'PASSED', '宝山校区南门', NULL, NULL, 'ON_SALE', 111246117974981119, 7, NULL, NULL, 0, NOW() - INTERVAL 25 DAY - INTERVAL 8 HOUR),
(19, 8, 1, 'SELL', 'ikbc C87 机械键盘 红轴', 'ikbc C87 红轴，键帽无亮键，手感正常，附原装拔键器。可视频验货。', 1, 199.00, '[]', 'PASSED', '东区9号楼', NULL, NULL, 'SOLD', 117426457862480070, 4, '宝山校区', '东区9号楼', 0, NOW() - INTERVAL 46 DAY - INTERVAL 8 HOUR),
(20, 8, 1, 'SELL', '罗技G102 游戏鼠标', '罗技 G102，灯效正常，微动无双击，买来打瓦吃灰了。', 1, 99.00, '[]', 'PASSED', '东区9号楼', NULL, NULL, 'SOLD', 123606797749979021, 5, '宝山校区', '东区9号楼', 0, NOW() - INTERVAL 33 DAY - INTERVAL 8 HOUR),
(21, 8, 1, 'SELL', 'Switch游戏卡带×3 马里奥赛车等', '三盘合出：马里奥赛车8、马车方程式、胡闹厨房2，卡带成色好。', 5, 59.00, '[]', 'PASSED', '东区9号楼', NULL, NULL, 'SOLD', 129787137637477972, 8, '宝山校区', '东区9号楼', 0, NOW() - INTERVAL 22 DAY - INTERVAL 8 HOUR),
(22, 8, 1, 'SELL', 'Switch Pro手柄 精灵球款', 'Pro 手柄精灵球限定款，摇杆无漂移，盒说齐。', 5, 129.00, '[]', 'PASSED', '东区9号楼', NULL, NULL, 'SOLD', 135967477524976923, 12, '宝山校区', '东区9号楼', 0, NOW() - INTERVAL 15 DAY - INTERVAL 8 HOUR),
(23, 8, 1, 'SELL', 'type-c编织数据线 1米', 'type-c 编织线 1 米，100W 快充协议全，用了俩月。', 1, 39.00, '[]', 'PASSED', '东区9号楼', NULL, NULL, 'SOLD', 142147817412475874, 20, '宝山校区', '东区9号楼', 0, NOW() - INTERVAL 8 DAY - INTERVAL 8 HOUR),
(24, 8, 1, 'SELL', '大号鼠标垫 90×40 加厚锁边', '90×40 加厚锁边鼠标垫，桌垫两用，洗过一次，九成新。', 1, 25.00, '[]', 'PASSED', '东区9号楼', NULL, NULL, 'SOLD', 148328157299974825, 28, '宝山校区', '东区9号楼', 0, NOW() - INTERVAL 3 DAY - INTERVAL 8 HOUR),
(25, 10, 1, 'SELL', '有道词典笔3代 专业版', '有道词典笔 3 代专业版，考研党自用，屏幕贴膜无划痕，笔尖套还在。', 1, 69.00, '[]', 'PASSED', '新世纪大学生村东门', NULL, NULL, 'RESERVED', 154508497187473776, 31, '宝山校区', '新世纪大学生村', 0, NOW() - INTERVAL 2 DAY - INTERVAL 8 HOUR),
(26, 10, 1, 'SELL', '考研英语阅读理解80篇 2026版', '考研英语阅读 80 篇 2026 版，前 10 篇有铅笔痕迹（可擦），其余全新。', 2, 88.00, '[]', 'PASSED', '宝山校区图书馆一楼', NULL, NULL, 'SOLD', 160688837074972727, 14, '宝山校区', '新世纪大学生村', 0, NOW() - INTERVAL 11 DAY - INTERVAL 8 HOUR),
(27, 10, 1, 'SELL', '新概念英语1-4全套教材', '新概念 1-4 全套，和外研社配套音频都在网盘，自取可送手写笔记。', 2, 60.00, '[]', 'PASSED', '新世纪大学生村东门', NULL, NULL, 'ON_SALE', 166869176962471678, 10, '宝山校区', '新世纪大学生村', 0, NOW() - INTERVAL 18 DAY - INTERVAL 8 HOUR),
(28, 9, 1, 'BUY', '求购二手显示器 24寸 2K', '求购 24 寸 2K 显示器，宿舍桌放得下就行， HDMI/DP 接口，东区自提。', 1, 200.00, '[]', 'PASSED', '东区11号楼', NULL, NULL, 'ON_SALE', 173049516849970629, 15, '宝山校区', '东区11号楼', 0, NOW() - INTERVAL 10 DAY - INTERVAL 8 HOUR),
(29, 11, 4, 'SELL', '捷安特ATX 二手自行车 已调刹车', '捷安特 ATX，骑了一学期，刹车变速刚调过，前筐赠送，闵行校区面交。', 1, 66.00, '[]', 'PASSED', '学生公寓5栋下', NULL, NULL, 'SOLD', 179229856737469580, 18, '闵行校区', '学生公寓5栋', 0, NOW() - INTERVAL 9 DAY - INTERVAL 8 HOUR),
(30, 12, 4, 'SELL', '小米台灯Pro 国行', '小米台灯 Pro 国行，亮度色温双调节，毕业清仓。', 4, 30.00, '[]', 'PASSED', '学生公寓5栋下', NULL, NULL, 'SOLD', 185410196624968531, 16, '闵行校区', '学生公寓5栋', 0, NOW() - INTERVAL 10 DAY - INTERVAL 8 HOUR),
(31, 11, 4, 'SELL', '考研自习室加厚坐垫', '考研自习室坐垫，加厚记忆棉，坐了两个月，洗过。', 2, 10.00, '[]', 'PASSED', '学生公寓5栋下', NULL, NULL, 'ON_SALE', 191590536512467482, 29, '闵行校区', '学生公寓5栋', 0, NOW() - INTERVAL 3 DAY - INTERVAL 8 HOUR),
(32, 12, 4, 'SELL', '宿舍折叠晾衣架 免打孔 加v看图', '免打孔折叠晾衣架，阳台浴室都能装，多的一个出。加 v 看实物视频：xxx198803', 3, 8.00, '[]', 'PASSED', '学生公寓5栋下', NULL, NULL, 'ON_SALE', 197770876399966433, 25, '闵行校区', '学生公寓5栋', 0, NOW() - INTERVAL 6 DAY - INTERVAL 8 HOUR),
(33, 6, 1, 'ERRAND', '帮取快递 菜鸟驿站→东区9号楼', '周三下午在东区驿站有 3 个包裹，帮忙拿到东区 9 号楼前台即可，一单一结。', 8, 5.00, '[]', 'PASSED', NULL, '东区菜鸟驿站', '东区9号楼', 'ON_SALE', 203951216287465384, 33, '宝山校区', '东区9号楼', 0, NOW() - INTERVAL 1 DAY - INTERVAL 8 HOUR),
(34, 5, 1, 'ERRAND', '帮带饭 二食堂→西区宿舍', '工作日中午帮带饭，二食堂到西区 3 号楼，跑腿费 6 元，饭钱见面给。', 8, 6.00, '[]', 'PASSED', NULL, '第二食堂', '西区3号楼', 'ON_SALE', 210131556174964335, 34, NULL, NULL, 0, NOW() - INTERVAL 1 DAY - INTERVAL 8 HOUR);

-- feed 序列推进到与商品 listing_revision 一致
UPDATE feed_sequence SET current_value = 34 WHERE id = 0;

-- 1.4 浏览量统计（每件商品一行，发布流程保证存在）
INSERT INTO item_view_stat (item_id, view_count) VALUES
(1, 321), (2, 87), (3, 25), (4, 64), (5, 189), (6, 6), (7, 13), (8, 20), (9, 27), (10, 34), (11, 41), (12, 8), (13, 15), (14, 33), (15, 51), (16, 36), (17, 12), (18, 45), (19, 143), (20, 24), (21, 31), (22, 38), (23, 5), (24, 12), (25, 76), (26, 26), (27, 33), (28, 58), (29, 7), (30, 14), (31, 21), (32, 28), (33, 9), (34, 6);

-- 1.5 标签与关联
INSERT INTO tag (id, name, normalized_name) VALUES
(1, '95新', '95新'), (2, '国行', '国行'), (3, '考研', '考研'), (4, '英语', '英语'), (5, 'Switch', 'switch'), (6, '键盘', '键盘'), (7, '宿舍神器', '宿舍神器'), (8, '自行车', '自行车'), (9, '含原装充电器', '含原装充电器'), (10, '毕业回血', '毕业回血');
INSERT INTO item_tag (item_id, tag_id) VALUES
(1, 1), (1, 2), (1, 9), (1, 10), (2, 10), (3, 10), (5, 9), (8, 10), (9, 7), (10, 3), (11, 3), (11, 4), (19, 6), (21, 5), (22, 5), (24, 7), (26, 3), (26, 4), (27, 4), (29, 8), (31, 3);

-- 1.6 订单（active_item_id 为生成列，不插入）
INSERT INTO trade_order (id, item_id, buyer_id, seller_id, price, status, cancel_reason, created_at, completed_at, cancelled_at)
VALUES
(1, 1, 6, 3, 1299.00, 'COMPLETED', NULL, NOW() - INTERVAL 26 DAY - INTERVAL 10 HOUR, NOW() - INTERVAL 25 DAY - INTERVAL 1 HOUR, NULL),
(2, 10, 6, 4, 45.00, 'COMPLETED', NULL, NOW() - INTERVAL 13 DAY - INTERVAL 10 HOUR, NOW() - INTERVAL 12 DAY - INTERVAL 1 HOUR, NULL),
(3, 26, 6, 10, 88.00, 'COMPLETED', NULL, NOW() - INTERVAL 11 DAY - INTERVAL 10 HOUR, NOW() - INTERVAL 10 DAY - INTERVAL 1 HOUR, NULL),
(4, 19, 9, 8, 199.00, 'COMPLETED', NULL, NOW() - INTERVAL 46 DAY - INTERVAL 10 HOUR, NOW() - INTERVAL 45 DAY - INTERVAL 1 HOUR, NULL),
(5, 20, 9, 8, 99.00, 'COMPLETED', NULL, NOW() - INTERVAL 33 DAY - INTERVAL 10 HOUR, NOW() - INTERVAL 32 DAY - INTERVAL 1 HOUR, NULL),
(6, 21, 9, 8, 59.00, 'COMPLETED', NULL, NOW() - INTERVAL 22 DAY - INTERVAL 10 HOUR, NOW() - INTERVAL 21 DAY - INTERVAL 1 HOUR, NULL),
(7, 22, 9, 8, 129.00, 'COMPLETED', NULL, NOW() - INTERVAL 15 DAY - INTERVAL 10 HOUR, NOW() - INTERVAL 14 DAY - INTERVAL 1 HOUR, NULL),
(8, 23, 9, 8, 39.00, 'COMPLETED', NULL, NOW() - INTERVAL 9 DAY - INTERVAL 10 HOUR, NOW() - INTERVAL 8 DAY - INTERVAL 1 HOUR, NULL),
(9, 24, 9, 8, 25.00, 'COMPLETED', NULL, NOW() - INTERVAL 4 DAY - INTERVAL 10 HOUR, NOW() - INTERVAL 3 DAY - INTERVAL 1 HOUR, NULL),
(10, 3, 6, 3, 79.00, 'COMPLETED', NULL, NOW() - INTERVAL 4 DAY - INTERVAL 10 HOUR, NOW() - INTERVAL 3 DAY - INTERVAL 1 HOUR, NULL),
(11, 7, 5, 3, 29.00, 'COMPLETED', NULL, NOW() - INTERVAL 6 DAY - INTERVAL 10 HOUR, NOW() - INTERVAL 5 DAY - INTERVAL 1 HOUR, NULL),
(12, 8, 10, 3, 59.00, 'COMPLETED', NULL, NOW() - INTERVAL 61 DAY - INTERVAL 10 HOUR, NOW() - INTERVAL 60 DAY - INTERVAL 1 HOUR, NULL),
(13, 9, 6, 3, 39.00, 'COMPLETED', NULL, NOW() - INTERVAL 91 DAY - INTERVAL 10 HOUR, NOW() - INTERVAL 90 DAY - INTERVAL 1 HOUR, NULL),
(14, 4, 6, 3, 129.00, 'CANCELLED', 'USER_CANCEL', NOW() - INTERVAL 3 DAY - INTERVAL 10 HOUR, NULL, NOW() - INTERVAL 2 DAY - INTERVAL 1 HOUR),
(15, 5, 6, 3, 899.00, 'WAITING_MEET', NULL, NOW() - INTERVAL 1 DAY - INTERVAL 2 HOUR, NULL, NULL),
(16, 25, 5, 10, 69.00, 'WAITING_MEET', NULL, NOW() - INTERVAL 1 DAY - INTERVAL 5 HOUR, NULL, NULL),
(17, 13, 13, 4, 35.00, 'CANCELLED', 'AUTO_CANCEL', NOW() - INTERVAL 16 DAY - INTERVAL 10 HOUR, NULL, NOW() - INTERVAL 15 DAY - INTERVAL 1 HOUR),
(18, 30, 11, 12, 30.00, 'COMPLETED', NULL, NOW() - INTERVAL 8 DAY - INTERVAL 10 HOUR, NOW() - INTERVAL 7 DAY - INTERVAL 1 HOUR, NULL),
(19, 29, 12, 11, 66.00, 'COMPLETED', NULL, NOW() - INTERVAL 7 DAY - INTERVAL 10 HOUR, NOW() - INTERVAL 6 DAY - INTERVAL 1 HOUR, NULL);

-- 1.7 钱包流水（按用户分组时间正序，balance_after 连续）
INSERT INTO wallet_log (user_id, type, amount, balance_after, order_id, remark, created_at)
VALUES
(3, 'INCOME', 39.00, 39.00, 13, '售出商品收入', NOW() - INTERVAL 90 DAY - INTERVAL 30 MINUTE),
(3, 'INCOME', 59.00, 98.00, 12, '售出商品收入', NOW() - INTERVAL 60 DAY - INTERVAL 30 MINUTE),
(3, 'RECHARGE', 100.00, 198.00, NULL, '校园卡余额充值', NOW() - INTERVAL 40 DAY - INTERVAL 30 MINUTE),
(3, 'INCOME', 1299.00, 1497.00, 1, '售出商品收入', NOW() - INTERVAL 25 DAY - INTERVAL 30 MINUTE),
(3, 'INCOME', 29.00, 1526.00, 11, '售出商品收入', NOW() - INTERVAL 5 DAY - INTERVAL 30 MINUTE),
(3, 'INCOME', 79.00, 1605.00, 10, '售出商品收入', NOW() - INTERVAL 3 DAY - INTERVAL 30 MINUTE),
(4, 'RECHARGE', 30.00, 30.00, NULL, '校园卡余额充值', NOW() - INTERVAL 60 DAY - INTERVAL 30 MINUTE),
(4, 'INCOME', 45.00, 75.00, 2, '售出商品收入', NOW() - INTERVAL 12 DAY - INTERVAL 30 MINUTE),
(5, 'RECHARGE', 200.00, 200.00, NULL, '校园卡余额充值', NOW() - INTERVAL 10 DAY - INTERVAL 30 MINUTE),
(5, 'PAYMENT', -29.00, 171.00, 11, '下单支付（订单#11）', NOW() - INTERVAL 6 DAY - INTERVAL 30 MINUTE),
(5, 'PAYMENT', -69.00, 102.00, 16, '下单支付（订单#16）', NOW() - INTERVAL 1 DAY - INTERVAL 5 HOUR),
(6, 'RECHARGE', 3000.00, 3000.00, NULL, '校园卡余额充值', NOW() - INTERVAL 120 DAY - INTERVAL 30 MINUTE),
(6, 'PAYMENT', -39.00, 2961.00, 13, '下单支付（订单#13）', NOW() - INTERVAL 91 DAY - INTERVAL 30 MINUTE),
(6, 'PAYMENT', -1299.00, 1662.00, 1, '下单支付（订单#1）', NOW() - INTERVAL 26 DAY - INTERVAL 30 MINUTE),
(6, 'PAYMENT', -45.00, 1617.00, 2, '下单支付（订单#2）', NOW() - INTERVAL 13 DAY - INTERVAL 30 MINUTE),
(6, 'PAYMENT', -88.00, 1529.00, 3, '下单支付（订单#3）', NOW() - INTERVAL 11 DAY - INTERVAL 30 MINUTE),
(6, 'PAYMENT', -79.00, 1450.00, 10, '下单支付（订单#10）', NOW() - INTERVAL 4 DAY - INTERVAL 30 MINUTE),
(6, 'PAYMENT', -129.00, 1321.00, 14, '下单支付（订单#14）', NOW() - INTERVAL 3 DAY - INTERVAL 30 MINUTE),
(6, 'REFUND', 129.00, 1450.00, 14, '订单取消退款（订单#14）', NOW() - INTERVAL 2 DAY - INTERVAL 30 MINUTE),
(6, 'PAYMENT', -899.00, 551.00, 15, '下单支付（订单#15）', NOW() - INTERVAL 1 DAY - INTERVAL 2 HOUR),
(7, 'RECHARGE', 50.00, 50.00, NULL, '校园卡余额充值', NOW() - INTERVAL 10 DAY - INTERVAL 30 MINUTE),
(8, 'RECHARGE', 50.00, 50.00, NULL, '校园卡余额充值', NOW() - INTERVAL 60 DAY - INTERVAL 30 MINUTE),
(8, 'INCOME', 199.00, 249.00, 4, '售出商品收入', NOW() - INTERVAL 45 DAY - INTERVAL 30 MINUTE),
(8, 'INCOME', 99.00, 348.00, 5, '售出商品收入', NOW() - INTERVAL 32 DAY - INTERVAL 30 MINUTE),
(8, 'INCOME', 59.00, 407.00, 6, '售出商品收入', NOW() - INTERVAL 21 DAY - INTERVAL 30 MINUTE),
(8, 'INCOME', 129.00, 536.00, 7, '售出商品收入', NOW() - INTERVAL 14 DAY - INTERVAL 30 MINUTE),
(8, 'INCOME', 39.00, 575.00, 8, '售出商品收入', NOW() - INTERVAL 8 DAY - INTERVAL 30 MINUTE),
(8, 'INCOME', 25.00, 600.00, 9, '售出商品收入', NOW() - INTERVAL 3 DAY - INTERVAL 30 MINUTE),
(9, 'RECHARGE', 600.00, 600.00, NULL, '校园卡余额充值', NOW() - INTERVAL 50 DAY - INTERVAL 30 MINUTE),
(9, 'PAYMENT', -199.00, 401.00, 4, '下单支付（订单#4）', NOW() - INTERVAL 46 DAY - INTERVAL 30 MINUTE),
(9, 'PAYMENT', -99.00, 302.00, 5, '下单支付（订单#5）', NOW() - INTERVAL 33 DAY - INTERVAL 30 MINUTE),
(9, 'PAYMENT', -59.00, 243.00, 6, '下单支付（订单#6）', NOW() - INTERVAL 22 DAY - INTERVAL 30 MINUTE),
(9, 'PAYMENT', -129.00, 114.00, 7, '下单支付（订单#7）', NOW() - INTERVAL 15 DAY - INTERVAL 30 MINUTE),
(9, 'PAYMENT', -39.00, 75.00, 8, '下单支付（订单#8）', NOW() - INTERVAL 9 DAY - INTERVAL 30 MINUTE),
(9, 'PAYMENT', -25.00, 50.00, 9, '下单支付（订单#9）', NOW() - INTERVAL 4 DAY - INTERVAL 30 MINUTE),
(10, 'RECHARGE', 100.00, 100.00, NULL, '校园卡余额充值', NOW() - INTERVAL 70 DAY - INTERVAL 30 MINUTE),
(10, 'PAYMENT', -59.00, 41.00, 12, '下单支付（订单#12）', NOW() - INTERVAL 61 DAY - INTERVAL 30 MINUTE),
(10, 'INCOME', 88.00, 129.00, 3, '售出商品收入', NOW() - INTERVAL 10 DAY - INTERVAL 30 MINUTE),
(11, 'RECHARGE', 100.00, 100.00, NULL, '校园卡余额充值', NOW() - INTERVAL 20 DAY - INTERVAL 30 MINUTE),
(11, 'PAYMENT', -30.00, 70.00, 18, '下单支付（订单#18）', NOW() - INTERVAL 8 DAY - INTERVAL 30 MINUTE),
(11, 'INCOME', 66.00, 136.00, 19, '售出商品收入', NOW() - INTERVAL 6 DAY - INTERVAL 30 MINUTE),
(12, 'RECHARGE', 100.00, 100.00, NULL, '校园卡余额充值', NOW() - INTERVAL 20 DAY - INTERVAL 30 MINUTE),
(12, 'INCOME', 30.00, 130.00, 18, '售出商品收入', NOW() - INTERVAL 7 DAY - INTERVAL 30 MINUTE),
(12, 'PAYMENT', -66.00, 64.00, 19, '下单支付（订单#19）', NOW() - INTERVAL 6 DAY - INTERVAL 30 MINUTE),
(13, 'RECHARGE', 100.00, 100.00, NULL, '校园卡余额充值', NOW() - INTERVAL 30 DAY - INTERVAL 30 MINUTE),
(13, 'PAYMENT', -35.00, 65.00, 17, '下单支付（订单#17）', NOW() - INTERVAL 16 DAY - INTERVAL 30 MINUTE),
(13, 'REFUND', 35.00, 100.00, 17, '订单取消退款（订单#17）', NOW() - INTERVAL 15 DAY - INTERVAL 30 MINUTE);

-- 1.8 经验流水（exp 合计与 sys_user.exp 一致；一次性任务带 dedup_key）
INSERT INTO exp_log (user_id, delta, exp_after, level_after, reason, rule_code, dedup_key, created_at)
VALUES
(3, 20, 20, 1, '首次完善校园资料', 'PROFILE_COMPLETED', 'PROFILE_COMPLETED', NOW() - INTERVAL 100 DAY - INTERVAL 6 HOUR),
(3, 5, 25, 1, '发布商品', 'ITEM_PUBLISHED', NULL, NOW() - INTERVAL 92 DAY - INTERVAL 4 HOUR),
(3, 40, 65, 2, '完成订单（卖出）', 'ORDER_SELLER', NULL, NOW() - INTERVAL 90 DAY - INTERVAL 1 HOUR),
(3, 5, 70, 2, '发布商品', 'ITEM_PUBLISHED', NULL, NOW() - INTERVAL 62 DAY - INTERVAL 4 HOUR),
(3, 40, 110, 2, '完成订单（卖出）', 'ORDER_SELLER', NULL, NOW() - INTERVAL 60 DAY - INTERVAL 1 HOUR),
(3, 10, 120, 2, '收到好评', 'GOOD_REVIEW', NULL, NOW() - INTERVAL 59 DAY - INTERVAL 3 HOUR),
(3, 5, 125, 2, '发布商品', 'ITEM_PUBLISHED', NULL, NOW() - INTERVAL 30 DAY - INTERVAL 4 HOUR),
(3, 20, 145, 2, '完成订单（卖出）（第 2 次成交）', 'ORDER_SELLER', NULL, NOW() - INTERVAL 25 DAY - INTERVAL 1 HOUR),
(3, 10, 155, 2, '收到好评', 'GOOD_REVIEW', NULL, NOW() - INTERVAL 24 DAY - INTERVAL 3 HOUR),
(3, 5, 160, 2, '发布商品', 'ITEM_PUBLISHED', NULL, NOW() - INTERVAL 12 DAY - INTERVAL 4 HOUR),
(3, 5, 165, 2, '发布商品', 'ITEM_PUBLISHED', NULL, NOW() - INTERVAL 7 DAY - INTERVAL 4 HOUR),
(3, 5, 170, 2, '发布商品', 'ITEM_PUBLISHED', NULL, NOW() - INTERVAL 7 DAY - INTERVAL 4 HOUR),
(3, 5, 175, 2, '发布商品', 'ITEM_PUBLISHED', NULL, NOW() - INTERVAL 6 DAY - INTERVAL 4 HOUR),
(3, 40, 215, 3, '完成订单（卖出）', 'ORDER_SELLER', NULL, NOW() - INTERVAL 5 DAY - INTERVAL 1 HOUR),
(3, 10, 225, 3, '收到好评', 'GOOD_REVIEW', NULL, NOW() - INTERVAL 4 DAY - INTERVAL 3 HOUR),
(3, 5, 230, 3, '发布商品', 'ITEM_PUBLISHED', NULL, NOW() - INTERVAL 3 DAY - INTERVAL 4 HOUR),
(3, 20, 250, 3, '完成订单（卖出）（第 3 次成交）', 'ORDER_SELLER', NULL, NOW() - INTERVAL 3 DAY - INTERVAL 1 HOUR),
(3, 5, 255, 3, '发布商品', 'ITEM_PUBLISHED', NULL, NOW() - INTERVAL 2 DAY - INTERVAL 4 HOUR),
(4, 20, 20, 1, '首次完善校园资料', 'PROFILE_COMPLETED', 'PROFILE_COMPLETED', NOW() - INTERVAL 50 DAY - INTERVAL 6 HOUR),
(4, 5, 25, 1, '发布商品', 'ITEM_PUBLISHED', NULL, NOW() - INTERVAL 20 DAY - INTERVAL 4 HOUR),
(4, 5, 30, 1, '发布商品', 'ITEM_PUBLISHED', NULL, NOW() - INTERVAL 16 DAY - INTERVAL 4 HOUR),
(4, 40, 70, 2, '完成订单（卖出）', 'ORDER_SELLER', NULL, NOW() - INTERVAL 12 DAY - INTERVAL 1 HOUR),
(4, 10, 80, 2, '收到好评', 'GOOD_REVIEW', NULL, NOW() - INTERVAL 11 DAY - INTERVAL 3 HOUR),
(4, 5, 85, 2, '发布商品', 'ITEM_PUBLISHED', NULL, NOW() - INTERVAL 8 DAY - INTERVAL 4 HOUR),
(4, 5, 90, 2, '发布商品', 'ITEM_PUBLISHED', NULL, NOW() - INTERVAL 6 DAY - INTERVAL 4 HOUR),
(5, 15, 15, 1, '完成订单（买入）', 'ORDER_BUYER', NULL, NOW() - INTERVAL 5 DAY - INTERVAL 2 HOUR),
(5, 5, 20, 1, '发布商品', 'ITEM_PUBLISHED', NULL, NOW() - INTERVAL 1 DAY - INTERVAL 4 HOUR),
(5, 5, 25, 1, '发布商品', 'ITEM_PUBLISHED', NULL, NOW() - INTERVAL 1 DAY - INTERVAL 4 HOUR),
(6, 20, 20, 1, '首次完善校园资料', 'PROFILE_COMPLETED', 'PROFILE_COMPLETED', NOW() - INTERVAL 150 DAY - INTERVAL 6 HOUR),
(6, 15, 35, 1, '完成订单（买入）', 'ORDER_BUYER', NULL, NOW() - INTERVAL 90 DAY - INTERVAL 2 HOUR),
(6, 8, 43, 1, '完成订单（买入）（第 2 次成交）', 'ORDER_BUYER', NULL, NOW() - INTERVAL 25 DAY - INTERVAL 2 HOUR),
(6, 15, 58, 1, '完成订单（买入）', 'ORDER_BUYER', NULL, NOW() - INTERVAL 12 DAY - INTERVAL 2 HOUR),
(6, 15, 73, 2, '完成订单（买入）', 'ORDER_BUYER', NULL, NOW() - INTERVAL 10 DAY - INTERVAL 2 HOUR),
(6, 5, 78, 2, '发布商品', 'ITEM_PUBLISHED', NULL, NOW() - INTERVAL 9 DAY - INTERVAL 4 HOUR),
(6, 5, 83, 2, '发布商品', 'ITEM_PUBLISHED', NULL, NOW() - INTERVAL 5 DAY - INTERVAL 4 HOUR),
(6, 8, 91, 2, '完成订单（买入）（第 3 次成交）', 'ORDER_BUYER', NULL, NOW() - INTERVAL 3 DAY - INTERVAL 2 HOUR),
(6, 5, 96, 2, '发布商品', 'ITEM_PUBLISHED', NULL, NOW() - INTERVAL 1 DAY - INTERVAL 4 HOUR),
(7, 5, 5, 1, '发布商品', 'ITEM_PUBLISHED', NULL, NOW() - INTERVAL 95 DAY - INTERVAL 4 HOUR),
(7, 5, 10, 1, '发布商品', 'ITEM_PUBLISHED', NULL, NOW() - INTERVAL 25 DAY - INTERVAL 4 HOUR),
(8, 20, 20, 1, '首次完善校园资料', 'PROFILE_COMPLETED', 'PROFILE_COMPLETED', NOW() - INTERVAL 60 DAY - INTERVAL 6 HOUR),
(8, 5, 25, 1, '发布商品', 'ITEM_PUBLISHED', NULL, NOW() - INTERVAL 46 DAY - INTERVAL 4 HOUR),
(8, 40, 65, 2, '完成订单（卖出）', 'ORDER_SELLER', NULL, NOW() - INTERVAL 45 DAY - INTERVAL 1 HOUR),
(8, 10, 75, 2, '收到好评', 'GOOD_REVIEW', NULL, NOW() - INTERVAL 44 DAY - INTERVAL 3 HOUR),
(8, 5, 80, 2, '发布商品', 'ITEM_PUBLISHED', NULL, NOW() - INTERVAL 33 DAY - INTERVAL 4 HOUR),
(8, 20, 100, 2, '完成订单（卖出）（第 2 次成交）', 'ORDER_SELLER', NULL, NOW() - INTERVAL 32 DAY - INTERVAL 1 HOUR),
(8, 10, 110, 2, '收到好评', 'GOOD_REVIEW', NULL, NOW() - INTERVAL 31 DAY - INTERVAL 3 HOUR),
(8, 5, 115, 2, '发布商品', 'ITEM_PUBLISHED', NULL, NOW() - INTERVAL 22 DAY - INTERVAL 4 HOUR),
(8, 20, 135, 2, '完成订单（卖出）（第 3 次成交）', 'ORDER_SELLER', NULL, NOW() - INTERVAL 21 DAY - INTERVAL 1 HOUR),
(8, 10, 145, 2, '收到好评', 'GOOD_REVIEW', NULL, NOW() - INTERVAL 20 DAY - INTERVAL 3 HOUR),
(8, 5, 150, 2, '发布商品', 'ITEM_PUBLISHED', NULL, NOW() - INTERVAL 15 DAY - INTERVAL 4 HOUR),
(8, 20, 170, 2, '完成订单（卖出）（第 4 次成交）', 'ORDER_SELLER', NULL, NOW() - INTERVAL 14 DAY - INTERVAL 1 HOUR),
(8, 10, 180, 3, '收到好评', 'GOOD_REVIEW', NULL, NOW() - INTERVAL 13 DAY - INTERVAL 3 HOUR),
(8, 5, 185, 3, '发布商品', 'ITEM_PUBLISHED', NULL, NOW() - INTERVAL 8 DAY - INTERVAL 4 HOUR),
(8, 20, 205, 3, '完成订单（卖出）（第 5 次成交）', 'ORDER_SELLER', NULL, NOW() - INTERVAL 8 DAY - INTERVAL 1 HOUR),
(8, 5, 210, 3, '发布商品', 'ITEM_PUBLISHED', NULL, NOW() - INTERVAL 3 DAY - INTERVAL 4 HOUR),
(8, 8, 218, 3, '完成订单（卖出）（第 6 次成交）', 'ORDER_SELLER', NULL, NOW() - INTERVAL 3 DAY - INTERVAL 1 HOUR),
(8, 10, 228, 3, '收到好评', 'GOOD_REVIEW', NULL, NOW() - INTERVAL 2 DAY - INTERVAL 3 HOUR),
(9, 15, 15, 1, '完成订单（买入）', 'ORDER_BUYER', NULL, NOW() - INTERVAL 45 DAY - INTERVAL 2 HOUR),
(9, 20, 35, 1, '首次完善校园资料', 'PROFILE_COMPLETED', 'PROFILE_COMPLETED', NOW() - INTERVAL 40 DAY - INTERVAL 6 HOUR),
(9, 8, 43, 1, '完成订单（买入）（第 2 次成交）', 'ORDER_BUYER', NULL, NOW() - INTERVAL 32 DAY - INTERVAL 2 HOUR),
(9, 8, 51, 1, '完成订单（买入）（第 3 次成交）', 'ORDER_BUYER', NULL, NOW() - INTERVAL 21 DAY - INTERVAL 2 HOUR),
(9, 8, 59, 1, '完成订单（买入）（第 4 次成交）', 'ORDER_BUYER', NULL, NOW() - INTERVAL 14 DAY - INTERVAL 2 HOUR),
(9, 5, 64, 2, '发布商品', 'ITEM_PUBLISHED', NULL, NOW() - INTERVAL 10 DAY - INTERVAL 4 HOUR),
(9, 8, 72, 2, '完成订单（买入）（第 5 次成交）', 'ORDER_BUYER', NULL, NOW() - INTERVAL 8 DAY - INTERVAL 2 HOUR),
(9, 3, 75, 2, '完成订单（买入）（第 6 次成交）', 'ORDER_BUYER', NULL, NOW() - INTERVAL 3 DAY - INTERVAL 2 HOUR),
(10, 15, 15, 1, '完成订单（买入）', 'ORDER_BUYER', NULL, NOW() - INTERVAL 60 DAY - INTERVAL 2 HOUR),
(10, 20, 35, 1, '首次完善校园资料', 'PROFILE_COMPLETED', 'PROFILE_COMPLETED', NOW() - INTERVAL 55 DAY - INTERVAL 6 HOUR),
(10, 5, 40, 1, '发布商品', 'ITEM_PUBLISHED', NULL, NOW() - INTERVAL 18 DAY - INTERVAL 4 HOUR),
(10, 5, 45, 1, '发布商品', 'ITEM_PUBLISHED', NULL, NOW() - INTERVAL 11 DAY - INTERVAL 4 HOUR),
(10, 40, 85, 2, '完成订单（卖出）', 'ORDER_SELLER', NULL, NOW() - INTERVAL 10 DAY - INTERVAL 1 HOUR),
(10, 10, 95, 2, '收到好评', 'GOOD_REVIEW', NULL, NOW() - INTERVAL 9 DAY - INTERVAL 3 HOUR),
(10, 5, 100, 2, '发布商品', 'ITEM_PUBLISHED', NULL, NOW() - INTERVAL 2 DAY - INTERVAL 4 HOUR),
(11, 20, 20, 1, '首次完善校园资料', 'PROFILE_COMPLETED', 'PROFILE_COMPLETED', NOW() - INTERVAL 15 DAY - INTERVAL 6 HOUR),
(11, 5, 25, 1, '发布商品', 'ITEM_PUBLISHED', NULL, NOW() - INTERVAL 9 DAY - INTERVAL 4 HOUR),
(11, 15, 40, 1, '完成订单（买入）', 'ORDER_BUYER', NULL, NOW() - INTERVAL 7 DAY - INTERVAL 2 HOUR),
(11, 40, 80, 2, '完成订单（卖出）', 'ORDER_SELLER', NULL, NOW() - INTERVAL 6 DAY - INTERVAL 1 HOUR),
(11, 10, 90, 2, '收到好评', 'GOOD_REVIEW', NULL, NOW() - INTERVAL 5 DAY - INTERVAL 3 HOUR),
(11, 5, 95, 2, '发布商品', 'ITEM_PUBLISHED', NULL, NOW() - INTERVAL 3 DAY - INTERVAL 4 HOUR),
(12, 20, 20, 1, '首次完善校园资料', 'PROFILE_COMPLETED', 'PROFILE_COMPLETED', NOW() - INTERVAL 15 DAY - INTERVAL 6 HOUR),
(12, 5, 25, 1, '发布商品', 'ITEM_PUBLISHED', NULL, NOW() - INTERVAL 10 DAY - INTERVAL 4 HOUR),
(12, 40, 65, 2, '完成订单（卖出）', 'ORDER_SELLER', NULL, NOW() - INTERVAL 7 DAY - INTERVAL 1 HOUR),
(12, 5, 70, 2, '发布商品', 'ITEM_PUBLISHED', NULL, NOW() - INTERVAL 6 DAY - INTERVAL 4 HOUR),
(12, 10, 80, 2, '收到好评', 'GOOD_REVIEW', NULL, NOW() - INTERVAL 6 DAY - INTERVAL 3 HOUR),
(12, 15, 95, 2, '完成订单（买入）', 'ORDER_BUYER', NULL, NOW() - INTERVAL 6 DAY - INTERVAL 2 HOUR),
(13, 20, 20, 1, '首次完善校园资料', 'PROFILE_COMPLETED', 'PROFILE_COMPLETED', NOW() - INTERVAL 25 DAY - INTERVAL 6 HOUR);

-- 1.9 交易评价（一单一评）
INSERT INTO trade_review (order_id, reviewer_id, target_id, rating, accurate, comment, created_at)
VALUES
(1, 6, 3, 5, 1, '成色和描述一致，屏幕无划痕，还送了壳，卖家超nice', NOW() - INTERVAL 24 DAY - INTERVAL 2 HOUR),
(2, 6, 4, 5, 1, '书很新，笔记都可擦，省了大几百', NOW() - INTERVAL 11 DAY - INTERVAL 2 HOUR - INTERVAL 1 MINUTE),
(3, 6, 10, 4, 0, '书角有点磨损，描述里没提，其他还行', NOW() - INTERVAL 9 DAY - INTERVAL 2 HOUR - INTERVAL 2 MINUTE),
(4, 9, 8, 5, 1, '键盘成色完美，键位全过，还录了视频', NOW() - INTERVAL 44 DAY - INTERVAL 2 HOUR - INTERVAL 3 MINUTE),
(5, 9, 8, 5, 1, '第二次买了，靠谱', NOW() - INTERVAL 31 DAY - INTERVAL 2 HOUR - INTERVAL 4 MINUTE),
(6, 9, 8, 4, 1, '卡带都正常，有一张包装旧了点', NOW() - INTERVAL 20 DAY - INTERVAL 2 HOUR - INTERVAL 5 MINUTE),
(7, 9, 8, 5, 1, '手柄很新，摇杆无漂移，好评', NOW() - INTERVAL 13 DAY - INTERVAL 2 HOUR - INTERVAL 6 MINUTE),
(8, 9, 8, 3, 0, '数据线接触有点松，不太满意', NOW() - INTERVAL 7 DAY - INTERVAL 2 HOUR - INTERVAL 7 MINUTE),
(9, 9, 8, 4, 1, '鼠标垫厚度正好，发货快', NOW() - INTERVAL 2 DAY - INTERVAL 2 HOUR - INTERVAL 8 MINUTE),
(11, 5, 3, 5, 1, '衣架结实，卖家很热情，自提还聊了半天', NOW() - INTERVAL 4 DAY - INTERVAL 2 HOUR - INTERVAL 9 MINUTE),
(12, 10, 3, 4, 1, '锅好用，就是内胆有个小瑕疵图里看不出', NOW() - INTERVAL 59 DAY - INTERVAL 2 HOUR - INTERVAL 10 MINUTE),
(13, 6, 3, 3, 0, '桌板有点晃，和描述有点差距', NOW() - INTERVAL 89 DAY - INTERVAL 2 HOUR - INTERVAL 11 MINUTE),
(18, 11, 12, 5, 1, '台灯亮度很好，学长人很好', NOW() - INTERVAL 6 DAY - INTERVAL 2 HOUR - INTERVAL 12 MINUTE),
(19, 12, 11, 4, 1, '车况不错，刹车确实调过了', NOW() - INTERVAL 5 DAY - INTERVAL 2 HOUR - INTERVAL 13 MINUTE);

-- 1.10 响应速度：可见会话的首响样本 + 汇总指标行
INSERT INTO chat_response_sample (sample_key, user_id, gap_seconds, created_at) VALUES
('3_6:2', 3, 180, NOW() - INTERVAL 3 DAY),
('3_5:7', 3, 120, NOW() - INTERVAL 3 DAY),
('8_9:9', 8, 300, NOW() - INTERVAL 3 DAY),
('6_10:11', 10, 21600, NOW() - INTERVAL 3 DAY);
INSERT INTO user_reputation_metric (user_id, sample_count, ewma_gap_seconds, last_sample_at) VALUES
(3, 18, 224, NOW() - INTERVAL 0 DAY - INTERVAL 12 HOUR),
(4, 6, 480, NOW() - INTERVAL 0 DAY - INTERVAL 20 HOUR),
(8, 9, 312, NOW() - INTERVAL 0 DAY - INTERVAL 10 HOUR),
(10, 8, 43200, NOW() - INTERVAL 2 DAY);

-- 1.11 聊天消息（conversation_id = 小ID_大ID；含系统通知与未读示例）
INSERT INTO chat_message (id, conversation_id, sender_id, receiver_id, content, related_item_id, is_read, source_event_id, created_at)
VALUES
(1, '3_6', 6, 3, '同学你好，iPhone 还在吗？电池效率多少呀', 1, 1, NULL, NOW() - INTERVAL 26 DAY),
(2, '3_6', 3, 6, '在的！电池效率 89%，95 新无拆修，图都是实拍', 1, 1, NULL, NOW() - INTERVAL 25 DAY - INTERVAL 23 HOUR - INTERVAL 57 MINUTE),
(3, '3_6', 6, 3, '1299 能包邮吗', 1, 1, NULL, NOW() - INTERVAL 25 DAY - INTERVAL 23 HOUR - INTERVAL 50 MINUTE),
(4, '3_6', 3, 6, '给你包邮，明天下午东区东门面交？', 1, 1, NULL, NOW() - INTERVAL 25 DAY - INTERVAL 23 HOUR - INTERVAL 35 MINUTE),
(5, '1_6', 1, 6, '你的订单已完成，记得评价卖家哦～', 1, 1, NULL, NOW() - INTERVAL 25 DAY),
(6, '3_5', 5, 3, '衣架还结实吗？可以自提吗', 7, 1, NULL, NOW() - INTERVAL 6 DAY - INTERVAL 2 HOUR),
(7, '3_5', 3, 5, '结实得很，搬家才出的，西区宿舍楼下自提就行', 7, 1, NULL, NOW() - INTERVAL 6 DAY - INTERVAL 1 HOUR - INTERVAL 58 MINUTE),
(8, '8_9', 9, 8, '老哥，键盘轴体有没有手感衰退', 19, 1, NULL, NOW() - INTERVAL 46 DAY - INTERVAL 1 HOUR),
(9, '8_9', 8, 9, '放心，红轴八成寿命，可以录打字视频给你看', 19, 1, NULL, NOW() - INTERVAL 46 DAY - INTERVAL 55 MINUTE),
(10, '6_10', 6, 10, '请问 80 篇是 2026 版吗？笔记多吗', 26, 1, NULL, NOW() - INTERVAL 11 DAY - INTERVAL 8 HOUR),
(11, '6_10', 10, 6, '不好意思刚看到，是 2026 版，前 10 篇有铅笔痕迹，可擦', 26, 1, NULL, NOW() - INTERVAL 11 DAY - INTERVAL 2 HOUR),
(12, '3_9', 9, 3, '吉他还在吗？想拿尤克里里加一点换，可小刀', 2, 0, NULL, NOW() - INTERVAL 0 DAY - INTERVAL 12 HOUR);

-- 1.12 收藏
INSERT INTO item_favorite (user_id, item_id, created_at) VALUES
(6, 5, NOW() - INTERVAL 2 DAY), (6, 2, NOW() - INTERVAL 1 DAY - INTERVAL 20 HOUR), (9, 27, NOW() - INTERVAL 3 DAY), (5, 13, NOW() - INTERVAL 0 DAY - INTERVAL 6 HOUR);

-- 1.13 内容治理链路演示：代写违规（已确认+处罚+待审申诉）与用户举报（待审核）
INSERT INTO violation_report (id, user_id, reporter_id, original_title, original_description, source, violation_type, violation_reason, matched_rules, rule_version, status, handler_id, handle_note, item_id, created_at, handled_at)
VALUES
(1, 7, NULL, '代写作业代做PPT 价格好说', '代写各科作业，代做 PPT 排版，价格好说，速度快质量高，可看样例。', 'LOCAL_RULE', 'KEYWORD_MATCH', '命中拼写变体规则：daixie ≈ 代写（Aho-Corasick 单趟匹配）', '["pinyin-daixie-v1"]', '2026.3', 'CONFIRMED', 2, '命中本地规则确认违规，商品下架并记录信誉处罚', 17, NOW() - INTERVAL 94 DAY, NOW() - INTERVAL 93 DAY),
(2, 12, 11, '宿舍折叠晾衣架 免打孔 加v看图', '免打孔折叠晾衣架，阳台浴室都能装，多的一个出。加 v 看实物视频：xxx198803', 'USER_REPORT', 'ADVERTISING', '疑似广告或站外引流：描述中包含微信号（xxx198803）', '[]', NULL, 'PENDING', NULL, NULL, 32, NOW() - INTERVAL 1 DAY - INTERVAL 4 HOUR, NULL);

-- 信誉处罚：周子昂 1 条生效中的内容警告（随时间线性衰减，可经申诉撤销）
INSERT INTO reputation_penalty (id, report_id, user_id, admin_id, type, points, reason, status, created_at, revoked_at)
VALUES (1, 1, 7, 2, 'CONTENT_WARNING', 5, '发布代写类违规内容，命中本地规则 daixie 拼写变体', 'ACTIVE', NOW() - INTERVAL 93 DAY, NULL);

-- 待审申诉：管理员可通过后审批量申诉 → 处罚撤销 + 商品恢复
INSERT INTO violation_appeal (id, report_id, item_id, user_id, reason, status, handler_id, handle_note, created_at, handled_at)
VALUES (1, 1, 17, 7, '标题是"代写"是接龙帮同学分享 PPT 模板，不是代写服务，请求复核恢复商品', 'PENDING', NULL, NULL, NOW() - INTERVAL 92 DAY, NULL);

-- 封禁日志：郑楠限时封禁（对应其订单 AUTO_CANCEL 自动撤单退款）
INSERT INTO violation_log (user_id, admin_id, type, reason, ban_days, created_at)
VALUES (13, 2, 'BAN_TEMP', '多次发布广告内容且拒不整改，限时封禁 22 天', 22, NOW() - INTERVAL 15 DAY);

-- 1.14 进行中的运营专题（首页 Banner）
INSERT INTO event_topic (title, start_time, end_time, filter_type, filter_category_id, filter_tags, banner_text, enabled, created_by, created_at, updated_at)
VALUES
('开学季数码专场', NOW() - INTERVAL 5 DAY, NOW() + INTERVAL 25 DAY, 'SELL', 1, NULL, '开学装备焕新，数码好价一站集齐', 1, 2, NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 5 DAY),
('考研资料漂流周', NOW() - INTERVAL 2 DAY, NOW() + INTERVAL 12 DAY, NULL, 2, '["考研", "英语"]', '上岸学长学姐的资料，接住这份好运', 1, 2, NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY);
