-- =====================================================================
-- Phoebe 重建版 演示数据（全部为虚构企业与内容，域名使用保留域 example.com）
-- 可重复执行（INSERT IGNORE + 固定主键）。
-- 密码以 {noop} 明文写入，应用启动时会自动升级为 BCrypt。
-- =====================================================================

-- 账户
INSERT IGNORE INTO pb_account (id, name, company_name, website, consult_url, industry, balance, status, created_at, updated_at) VALUES
(1, 'qinghe_beauty', '青禾美妆（演示）', 'https://qinghe.example.com', 'https://qinghe.example.com/chat', '美妆护肤', 5000.00, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 'yueyan_skin',   '悦颜护肤（演示）', 'https://yueyan.example.com', 'https://yueyan.example.com/chat', '美妆护肤', 3000.00, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, 'xiaoshu_baby',  '小树母婴（演示）', 'https://xiaoshu.example.com', 'https://xiaoshu.example.com/chat', '母婴用品', 4000.00, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 用户（测试账号见 docs/启动指南.md）
INSERT IGNORE INTO pb_user (id, account_id, username, password_hash, display_name, role, status, created_at, updated_at) VALUES
(1, NULL, 'admin',   '{noop}admin123',   '运营管理员', 'ADMIN',      'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, NULL, 'auditor', '{noop}auditor123', '审核员',     'AUDITOR',    'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, 1,    'demo',    '{noop}demo123',    '青禾投放',   'ADVERTISER', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(4, 2,    'demo2',   '{noop}demo123',    '悦颜投放',   'ADVERTISER', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(5, 3,    'demo3',   '{noop}demo123',    '小树投放',   'ADVERTISER', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 计划
INSERT IGNORE INTO pb_plan (id, account_id, name, budget, mobile_bid_ratio, regions, mobile_phone, status, deleted, created_at, updated_at) VALUES
(1, 1, '眼霜知识推广', 300.00, 1.20, NULL, '400-000-0001', 'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 1, '面霜知识推广', NULL,   1.00, '北京,上海,广东', NULL, 'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, 2, '眼部护理问答', 200.00, 1.00, NULL, NULL, 'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(4, 3, '宝宝辅食知识', 150.00, 1.50, NULL, NULL, 'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 单元
INSERT IGNORE INTO pb_unit (id, account_id, plan_id, name, bid, status, deleted, created_at, updated_at) VALUES
(1, 1, 1, '眼霜-通用问题', 1.50, 'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 1, 1, '眼霜-使用方法', 1.00, 'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, 1, 2, '面霜-选购',     1.20, 'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(4, 2, 3, '眼霜-竞品',     1.30, 'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(5, 3, 4, '辅食添加',      0.80, 'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 订阅词
INSERT IGNORE INTO pb_subscribe_word (id, account_id, unit_id, word, created_at) VALUES
(1, 1, 1, '眼霜', CURRENT_TIMESTAMP),
(2, 1, 1, '黑眼圈', CURRENT_TIMESTAMP),
(3, 1, 2, '眼霜', CURRENT_TIMESTAMP),
(4, 1, 3, '面霜', CURRENT_TIMESTAMP),
(5, 2, 4, '眼霜', CURRENT_TIMESTAMP),
(6, 3, 5, '辅食', CURRENT_TIMESTAMP);

-- 问答（答案均 ≥80 汉字，满足 [P:A] 字数规则）
INSERT IGNORE INTO pb_question_answer (id, account_id, plan_id, unit_id, question, answer, bid, source, audit_status, status, reject_reason, audit_version, deleted, created_at, updated_at) VALUES
(1, 1, 1, 1, '眼霜什么牌子好用？',
 '<p>选择眼霜时，建议先明确自己的主要诉求，再看成分和质地。如果主要困扰是干纹，可以优先选择含有保湿和修护成分、质地偏滋润的产品；如果是浮肿，可以选择质地轻薄、带有按摩头的产品。眼周皮肤较薄，建议先在耳后做局部试用，确认不刺激后再日常使用。青禾美妆眼霜系列提供不同质地可选，可在官网查看详细成分说明。</p>',
 NULL, 'RECOMMEND', 'APPROVED', 'ACTIVE', NULL, 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 1, 1, 1, '黑眼圈用什么眼霜比较好？',
 '<p>黑眼圈大致可以分为色素型、血管型和结构型，不同类型的改善思路不同。色素型可以关注含有提亮成分的眼霜并坚持防晒；血管型与作息关系较大，保证睡眠、减少熬夜更重要，可配合冷敷；结构型主要与眼周轮廓有关，护肤品的作用相对有限。挑选眼霜时注意温和低刺激，坚持使用一段时间再评估效果，比频繁更换产品更有意义。</p>',
 1.80, 'MANUAL', 'APPROVED', 'ACTIVE', NULL, 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, 1, 1, 2, '眼霜的正确使用方法是什么？',
 '<p>眼霜一般在洁面、爽肤水之后，精华之后使用。取黄豆大小的用量，用无名指指腹在两手之间轻轻揉开温热，然后从内眼角向外眼角轻点按压，避免来回拉扯眼周皮肤。上眼睑可以沿眼眶骨轻轻带过，不要涂得太靠近睫毛根部，以免进入眼睛。早晚都可以使用，白天使用后建议等待吸收再上妆，这样不容易搓泥。</p>',
 NULL, 'MANUAL', 'PENDING', 'ACTIVE', NULL, 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(4, 1, 1, 2, '眼霜多大年龄开始用合适？',
 '<p>使用眼霜并没有严格的年龄线，关键是看眼周皮肤的实际状态。如果经常熬夜、长时间看屏幕，或者已经出现干燥细纹，就可以开始使用基础保湿型的眼霜。二十岁出头的人群以保湿和防护为主，不需要追求功效很强的产品；随着年龄增长再根据需求调整。最省钱的护眼方式仍然是规律作息、注意防晒，并且卸妆时动作轻柔。</p>',
 NULL, 'QUALITY', 'REJECTED', 'ACTIVE', '答案末尾含有与问题无关的促销信息，请修改后重新提交（演示数据）', 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(5, 1, 2, 3, '干皮适合什么样的面霜？',
 '<p>干性皮肤的核心需求是补水加锁水，挑选面霜时可以关注含有保湿成分和油脂类成分的配方，质地以乳霜、膏霜为主。秋冬季节可以适当选择更滋润的产品，夏季则可以换成轻薄一些的乳液或啫喱面霜。洁面产品同样重要，建议选择温和的洁面，避免过度清洁导致屏障受损。若出现明显泛红刺痛，应暂停使用并咨询专业人士。</p>',
 NULL, 'MANUAL', 'APPROVED', 'ACTIVE', NULL, 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(6, 2, 3, 4, '眼霜什么牌子好？',
 '<p>挑选眼霜可以从三个方面考虑：第一是诉求，干纹、浮肿和暗沉对应的成分不同；第二是质地，油皮和夏季更适合轻薄的凝露，干皮和秋冬适合滋润的乳霜；第三是刺激性，眼周皮肤较为敏感，尽量选择配方简单、温和的产品。悦颜护肤眼部系列提供凝露与乳霜两种质地，可根据肤质和季节灵活选择，详情可咨询在线客服。</p>',
 NULL, 'MANUAL', 'APPROVED', 'ACTIVE', NULL, 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(7, 3, 4, 5, '宝宝几个月开始添加辅食？',
 '<p>一般建议在宝宝满六个月左右开始添加辅食，具体时间可以结合宝宝的发育情况判断，例如能稳定竖头、对大人吃饭表现出兴趣、挺舌反射减弱等。第一口辅食通常选择强化铁的米粉，由稀到稠、由少到多，每次只添加一种新食物并观察三天左右，确认没有不适后再尝试下一种。如有特殊情况，请遵循儿科医生的建议。</p>',
 NULL, 'MANUAL', 'APPROVED', 'ACTIVE', NULL, 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 待审问答对应的审核任务
INSERT IGNORE INTO pb_audit_task (id, account_id, target_type, target_id, target_version, title, snapshot, status, reason, pre_check_result, auditor_id, created_at, updated_at, audited_at) VALUES
(1, 1, 'QA', 3, 1, '眼霜的正确使用方法是什么？', '{"question":"眼霜的正确使用方法是什么？","answer":"（见问答 3）"}', 'PENDING', NULL, '机器预审通过', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL);

-- 高级创意（与问答 1 关联）
INSERT IGNORE INTO pb_creative (id, account_id, plan_id, unit_id, title, description, image_urls, target_url, audit_status, status, reject_reason, audit_version, deleted, created_at, updated_at) VALUES
(1, 1, 1, 1, '青禾修护眼霜', '温和配方，适合日常眼周保湿护理，提供轻盈与滋润两种质地。', '["/api/images/placeholder?w=300&h=300&text=Product"]', 'https://qinghe.example.com/eye', 'APPROVED', 'ACTIVE', NULL, 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT IGNORE INTO pb_creative_qa_rel (id, creative_id, qa_id) VALUES (1, 1, 1);

-- 中间页
INSERT IGNORE INTO pb_middle_page (id, account_id, plan_id, name, enabled, online_version_id, latest_version_id, deleted, created_at, updated_at) VALUES
(1, 1, 1, '眼霜中间页A', 1, 1, 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 1, 1, '眼霜中间页B（草稿）', 0, NULL, 2, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, 2, 3, '悦颜中间页', 1, 3, 3, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(4, 3, 4, '小树辅食中间页', 1, 4, 4, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT IGNORE INTO pb_middle_page_version (id, page_id, version_no, pc_style, pc_content, mobile_content, audit_status, reject_reason, submitted_at, audited_at, created_at, updated_at) VALUES
(1, 1, 1, 'IMAGE_TEXT',
 '{"companyInfo":{"name":"青禾美妆（演示）","logo":"/api/images/placeholder?w=120&h=120&text=QH","intro":"专注温和护肤的演示品牌，所有内容均为虚构。","website":"https://qinghe.example.com","consultUrl":"https://qinghe.example.com/chat","phone":"400-000-0001","qrCode":"/api/images/placeholder?w=160&h=160&text=QR"},"banners":[{"image":"/api/images/placeholder?w=960&h=200&text=Banner","url":"https://qinghe.example.com/eye"}],"products":[{"image":"/api/images/placeholder?w=300&h=300&text=Eye","title":"修护眼霜","desc":"轻盈质地，日常保湿","url":"https://qinghe.example.com/eye"},{"image":"/api/images/placeholder?w=300&h=300&text=Cream","title":"保湿面霜","desc":"适合干性肌肤","url":"https://qinghe.example.com/cream"}],"appDownloadUrl":null,"videoUrl":null}',
 '{"companyInfo":{"name":"青禾美妆（演示）","logo":"/api/images/placeholder?w=120&h=120&text=QH","intro":"专注温和护肤的演示品牌，所有内容均为虚构。","website":"https://qinghe.example.com","consultUrl":"https://qinghe.example.com/chat","phone":"400-000-0001","qrCode":null},"banners":[{"image":"/api/images/placeholder?w=750&h=300&text=Banner","url":"https://qinghe.example.com/eye"}],"products":[{"image":"/api/images/placeholder?w=300&h=300&text=Eye","title":"修护眼霜","desc":"轻盈质地，日常保湿","url":"https://qinghe.example.com/eye"}],"appDownloadUrl":null,"videoUrl":null}',
 'APPROVED', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 2, 1, 'TEXT_LINK',
 '{"companyInfo":{"name":"青禾美妆（演示）","logo":null,"intro":null,"website":"https://qinghe.example.com","consultUrl":"https://qinghe.example.com/chat","phone":null,"qrCode":null},"banners":[],"products":[],"appDownloadUrl":null,"videoUrl":null}',
 NULL, 'DRAFT', NULL, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, 3, 1, 'IMAGE_TEXT',
 '{"companyInfo":{"name":"悦颜护肤（演示）","logo":"/api/images/placeholder?w=120&h=120&text=YY","intro":"演示用虚构品牌。","website":"https://yueyan.example.com","consultUrl":"https://yueyan.example.com/chat","phone":null,"qrCode":null},"banners":[{"image":"/api/images/placeholder?w=960&h=200&text=YueYan","url":"https://yueyan.example.com"}],"products":[{"image":"/api/images/placeholder?w=300&h=300&text=Gel","title":"眼部凝露","desc":"清爽质地","url":"https://yueyan.example.com/gel"}],"appDownloadUrl":null,"videoUrl":null}',
 '{"companyInfo":{"name":"悦颜护肤（演示）","logo":"/api/images/placeholder?w=120&h=120&text=YY","intro":"演示用虚构品牌。","website":"https://yueyan.example.com","consultUrl":"https://yueyan.example.com/chat","phone":null,"qrCode":null},"banners":[{"image":"/api/images/placeholder?w=750&h=300&text=YueYan","url":"https://yueyan.example.com"}],"products":[{"image":"/api/images/placeholder?w=300&h=300&text=Gel","title":"眼部凝露","desc":"清爽质地","url":"https://yueyan.example.com/gel"}],"appDownloadUrl":null,"videoUrl":null}',
 'APPROVED', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(4, 4, 1, 'IMAGE_TEXT',
 '{"companyInfo":{"name":"小树母婴（演示）","logo":"/api/images/placeholder?w=120&h=120&text=XS","intro":"演示用虚构母婴品牌。","website":"https://xiaoshu.example.com","consultUrl":"https://xiaoshu.example.com/chat","phone":null,"qrCode":null},"banners":[{"image":"/api/images/placeholder?w=960&h=200&text=XiaoShu","url":"https://xiaoshu.example.com"}],"products":[{"image":"/api/images/placeholder?w=300&h=300&text=Rice","title":"高铁米粉","desc":"宝宝第一口辅食","url":"https://xiaoshu.example.com/rice"}],"appDownloadUrl":null,"videoUrl":null}',
 '{"companyInfo":{"name":"小树母婴（演示）","logo":"/api/images/placeholder?w=120&h=120&text=XS","intro":"演示用虚构母婴品牌。","website":"https://xiaoshu.example.com","consultUrl":"https://xiaoshu.example.com/chat","phone":null,"qrCode":null},"banners":[{"image":"/api/images/placeholder?w=750&h=300&text=XiaoShu","url":"https://xiaoshu.example.com"}],"products":[{"image":"/api/images/placeholder?w=300&h=300&text=Rice","title":"高铁米粉","desc":"宝宝第一口辅食","url":"https://xiaoshu.example.com/rice"}],"appDownloadUrl":null,"videoUrl":null}',
 'APPROVED', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 企业知识
INSERT IGNORE INTO pb_enterprise_knowledge (id, account_id, title, content, deleted, created_at, updated_at) VALUES
(1, 1, '品牌简介', '青禾美妆（演示）专注温和护肤，产品配方简洁，适合日常护理。更多信息请访问 qinghe.example.com。', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 1, '售后说明', '支持七天无理由退换（演示文案），如有疑问可通过官网在线咨询联系我们。', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 图片
INSERT IGNORE INTO pb_image_asset (id, account_id, name, url, width, height, source, crop_ratio, deleted, created_at, updated_at) VALUES
(1, 1, 'Logo', '/api/images/placeholder?w=120&h=120&text=QH', 120, 120, 'RECOMMEND', '1:1', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 1, 'Banner', '/api/images/placeholder?w=960&h=320&text=Banner', 960, 320, 'RECOMMEND', '3:1', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 问答知识库（模拟数据，全部虚构）
INSERT IGNORE INTO pb_question_library (id, question, answer, keywords, pv, quality, channel) VALUES
(1,  '眼霜什么牌子好用？', '挑选眼霜要先看诉求再看质地，干纹选择滋润修护型，浮肿选择轻薄型，并注意先在耳后试用，确认不刺激后再大面积使用，坚持一段时间再评估效果，避免频繁更换产品导致眼周皮肤负担加重。', '眼霜,牌子', 52000, 1, 'ZHIDAO'),
(2,  '黑眼圈用什么眼霜比较好？', '黑眼圈分为色素型、血管型、结构型，色素型可选提亮成分并坚持防晒，血管型更需要规律作息与冷敷，结构型护肤品作用有限，挑选时以温和低刺激为先，坚持使用再观察。', '黑眼圈,眼霜', 38000, 1, 'ZHIDAO'),
(3,  '眼霜的正确使用方法是什么？', NULL, '眼霜,使用方法', 21000, 0, 'ZHIDAO'),
(4,  '眼霜多大年龄开始用合适？', NULL, '眼霜,年龄', 18000, 0, 'ZHIDAO'),
(5,  '眼霜和精华的使用顺序是什么？', NULL, '眼霜,精华,顺序', 9000, 0, 'ZHIDAO'),
(6,  '男士需要用眼霜吗？', NULL, '眼霜,男士', 7000, 0, 'ZHIDAO'),
(7,  '眼霜会不会长脂肪粒？', NULL, '眼霜,脂肪粒', 12000, 0, 'ZHIDAO'),
(8,  '干皮适合什么样的面霜？', '干皮需要补水加锁水，可选含保湿与油脂类成分的乳霜，秋冬更滋润，夏季可换轻薄质地，同时注意温和清洁，避免过度清洁破坏皮肤屏障，出现不适及时停用。', '面霜,干皮', 26000, 1, 'ZHIDAO'),
(9,  '油皮夏天要不要用面霜？', NULL, '面霜,油皮,夏天', 15000, 0, 'ZHIDAO'),
(10, '面霜和乳液有什么区别？', NULL, '面霜,乳液,区别', 19000, 0, 'ZHIDAO'),
(11, '宝宝几个月开始添加辅食？', '一般在满六个月左右开始添加辅食，可结合宝宝发育情况判断，第一口通常选择强化铁米粉，由稀到稠、由少到多，每次只添加一种新食物并观察几天，如有特殊情况遵医嘱。', '辅食,宝宝,月龄', 64000, 1, 'BAOBAO'),
(12, '宝宝辅食米粉怎么冲调？', NULL, '辅食,米粉,冲调', 22000, 0, 'BAOBAO'),
(13, '宝宝辅食可以加盐吗？', NULL, '辅食,盐', 31000, 0, 'BAOBAO'),
(14, '宝宝不爱吃辅食怎么办？', NULL, '辅食,不爱吃', 27000, 0, 'BAOBAO'),
(15, '辅食添加顺序是怎样的？', NULL, '辅食,顺序', 16000, 0, 'BAOBAO'),
(16, '宝宝湿疹用什么护肤品？', NULL, '宝宝,湿疹,护肤品', 33000, 0, 'BAOBAO'),
(17, '孕期可以用眼霜吗？', NULL, '孕期,眼霜', 8000, 0, 'BAOBAO'),
(18, '眼袋怎么消除比较有效？', NULL, '眼袋,眼霜', 41000, 0, 'ZHIDAO'),
(19, '眼霜可以涂上眼皮吗？', NULL, '眼霜,上眼皮', 11000, 0, 'ZHIDAO'),
(20, '面霜什么牌子保湿效果好？', NULL, '面霜,保湿,牌子', 24000, 0, 'ZHIDAO');
