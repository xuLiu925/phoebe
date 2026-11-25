package com.rebuild.phoebe.domain.service;

import com.rebuild.phoebe.common.BizException;
import com.rebuild.phoebe.common.Evidence;
import com.rebuild.phoebe.common.EvidenceLevel;
import com.rebuild.phoebe.domain.entity.Plan;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 计划级定向：投放时段与 IP 排除。
 * <p>
 * 字段存在 [P:A 截图：新建推广计划表单中的“投放时段：不限时间/自选时间”“IP排除：不限IP/排除IP”]；
 * 时段粒度（每周 7×24 小时）与 IP 格式（精确 IP、x.x.x.* 段）参照百度搜索推广的常见做法 [I]，原产品具体规则 [U]。
 */
@Evidence(level = EvidenceLevel.INFERRED, source = "A", note = "字段存在为公开资料；粒度与格式为推测")
public final class TargetingRules {
    public static final int SLOTS = 7 * 24;
    private static final Pattern IP = Pattern.compile("^(\\d{1,3}|\\*)(\\.(\\d{1,3}|\\*)){3}$");

    private TargetingRules() {
    }

    /** 校验并规范化投放时段；全选视为“不限”，返回 null。 */
    public static String normalizeSchedule(String schedule) {
        if (schedule == null || schedule.isBlank()) {
            return null;
        }
        String s = schedule.trim();
        if (s.length() != SLOTS || !s.matches("[01]+")) {
            throw BizException.badRequest("投放时段格式错误：需为 168 位 0/1");
        }
        if (!s.contains("1")) {
            throw BizException.badRequest("投放时段至少选择 1 个小时");
        }
        return s.contains("0") ? s : null;
    }

    /** 某时刻是否在计划投放时段内。下标 = (周几-1)*24 + 小时，周一为 1。 */
    public static boolean inSchedule(Plan plan, LocalDateTime time) {
        String s = plan.getSchedule();
        if (s == null || s.length() != SLOTS) {
            return true;
        }
        int idx = (time.getDayOfWeek().getValue() - 1) * 24 + time.getHour();
        return s.charAt(idx) == '1';
    }

    /** 校验并规范化 IP 排除列表，返回逗号分隔串；空列表返回 null。 */
    public static String normalizeIps(List<String> ips, int max) {
        if (ips == null) {
            return null;
        }
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String raw : ips) {
            if (raw == null) {
                continue;
            }
            for (String part : raw.split("[,，\\s]+")) {
                String ip = part.trim();
                if (ip.isEmpty()) {
                    continue;
                }
                if (!validIp(ip)) {
                    throw BizException.badRequest("IP 格式错误：" + ip + "（支持精确 IP 或 x.x.x.* 形式的 IP 段）");
                }
                set.add(ip);
            }
        }
        if (set.size() > max) {
            throw BizException.limit("每个计划最多排除 " + max + " 个 IP / IP 段");
        }
        return set.isEmpty() ? null : String.join(",", set);
    }

    static boolean validIp(String ip) {
        if (!IP.matcher(ip).matches()) {
            return false;
        }
        String[] seg = ip.split("\\.");
        boolean wildcard = false;
        for (int i = 0; i < 4; i++) {
            if (seg[i].equals("*")) {
                if (i == 0) {
                    return false;
                }
                wildcard = true;
            } else {
                if (wildcard || Integer.parseInt(seg[i]) > 255) {
                    return false;
                }
            }
        }
        return true;
    }

    public static List<String> ipList(Plan plan) {
        List<String> list = new ArrayList<>();
        if (plan.getExcludedIps() != null) {
            for (String s : plan.getExcludedIps().split(",")) {
                if (!s.isBlank()) {
                    list.add(s.trim());
                }
            }
        }
        return list;
    }

    /** 访客 IP 是否被计划排除。 */
    public static boolean ipExcluded(Plan plan, String visitorIp) {
        if (visitorIp == null || visitorIp.isBlank()) {
            return false;
        }
        String[] v = visitorIp.trim().split("\\.");
        if (v.length != 4) {
            return false;
        }
        for (String rule : ipList(plan)) {
            String[] r = rule.split("\\.");
            boolean match = true;
            for (int i = 0; i < 4; i++) {
                if (!r[i].equals("*") && !r[i].equals(v[i])) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return true;
            }
        }
        return false;
    }
}
