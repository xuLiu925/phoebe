package com.rebuild.phoebe.web.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rebuild.phoebe.domain.entity.QuestionAnswer;
import com.rebuild.phoebe.domain.entity.User;
import com.rebuild.phoebe.domain.enums.AuditStatus;
import com.rebuild.phoebe.domain.enums.Channel;
import com.rebuild.phoebe.domain.enums.Device;
import com.rebuild.phoebe.domain.mapper.QuestionAnswerMapper;
import com.rebuild.phoebe.domain.mapper.UserMapper;
import com.rebuild.phoebe.domain.service.EligibilityService;
import com.rebuild.phoebe.domain.service.StatService;
import com.rebuild.phoebe.domain.entity.MiddlePage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;

/**
 * 启动初始化：
 * 1. 将演示数据中 {noop} 明文密码升级为 BCrypt；
 * 2. H2 下显式主键插入不会推进自增序列，这里将各表自增起点重置到 max(id)+1；
 * 3. 日报为空时生成最近 7 天演示统计（模拟数据）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupInitializer implements ApplicationRunner {
    private static final List<String> TABLES = List.of("pb_account", "pb_user", "pb_plan", "pb_unit", "pb_subscribe_word",
            "pb_question_answer", "pb_creative", "pb_creative_qa_rel", "pb_middle_page", "pb_middle_page_version",
            "pb_enterprise_knowledge", "pb_image_asset", "pb_audit_task", "pb_question_library", "pb_budget_limit_log");

    private static final String[] SEED_REGIONS = {"北京", "上海", "广东", "浙江", "江苏"};
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final UserMapper userMapper;
    private final QuestionAnswerMapper qaMapper;
    private final PasswordEncoder passwordEncoder;
    private final StatService statService;
    private final EligibilityService eligibility;

    @Value("${phoebe.seed-demo-stats:true}")
    private boolean seedStats;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        resetH2Identities();
        upgradePasswords();
        if (seedStats && statService.isEmpty()) {
            seedStats();
        }
    }

    private void resetH2Identities() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            if (!c.getMetaData().getDatabaseProductName().toLowerCase().contains("h2")) {
                return;
            }
        }
        for (String t : TABLES) {
            Long max = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM " + t, Long.class);
            jdbc.execute("ALTER TABLE " + t + " ALTER COLUMN id RESTART WITH " + (max + 1));
        }
    }

    private void upgradePasswords() {
        for (User u : userMapper.selectList(new LambdaQueryWrapper<User>().likeRight(User::getPasswordHash, "{noop}"))) {
            u.setPasswordHash(passwordEncoder.encode(u.getPasswordHash().substring("{noop}".length())));
            userMapper.updateById(u);
        }
    }

    private void seedStats() {
        Random rnd = new Random(42);
        List<QuestionAnswer> qas = qaMapper.selectList(new LambdaQueryWrapper<QuestionAnswer>()
                .eq(QuestionAnswer::getAuditStatus, AuditStatus.APPROVED));
        for (int d = 7; d >= 1; d--) {
            LocalDate date = LocalDate.now().minusDays(d);
            for (QuestionAnswer qa : qas) {
                MiddlePage mp = eligibility.enabledValidPage(qa.getPlanId());
                boolean baby = qa.getAccountId() == 3L;
                Channel[] channels = baby ? new Channel[]{Channel.BAOBAO, Channel.BAOBAO_APP} : new Channel[]{Channel.ZHIDAO, Channel.SEARCH};
                for (Device device : Device.values()) {
                    for (Channel ch : channels) {
                        String region = SEED_REGIONS[rnd.nextInt(SEED_REGIONS.length)];
                        long imp = 20 + rnd.nextInt(80);
                        long clk = Math.round(imp * (0.02 + rnd.nextDouble() * 0.06));
                        BigDecimal cpc = BigDecimal.valueOf(0.5 + rnd.nextDouble()).setScale(2, RoundingMode.HALF_UP);
                        statService.addQa(qa, device, ch, region, date, imp, clk, cpc.multiply(BigDecimal.valueOf(clk)));
                        if (mp != null) {
                            statService.addMiddlePage(qa.getAccountId(), qa.getPlanId(), mp.getId(), device, ch, region, date, clk, Math.round(clk * 0.3));
                        }
                    }
                }
            }
        }
        log.info("已生成最近 7 天演示统计数据（模拟）");
    }
}
