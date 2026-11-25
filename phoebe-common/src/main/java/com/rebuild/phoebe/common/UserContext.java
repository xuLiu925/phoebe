package com.rebuild.phoebe.common;

/**
 * 当前请求的操作人上下文（由 web 层过滤器写入）。
 * 原项目大概率接入百度一站式/SSO 登录 [I]，此处为本地 JWT 新建实现。
 */
public final class UserContext {

    public enum Role { ADVERTISER, AUDITOR, ADMIN }

    public record CurrentUser(Long userId, String username, Role role, Long accountId) {
    }

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    /** 当前操作的广告账户 id；没有账户上下文时抛 403。 */
    public static Long requireAccountId() {
        CurrentUser u = HOLDER.get();
        if (u == null || u.accountId() == null) {
            throw new BizException(ErrorCode.FORBIDDEN, "当前用户未绑定广告账户（管理员可通过 X-Account-Id 代登录）");
        }
        return u.accountId();
    }

    public static Long userIdOrNull() {
        CurrentUser u = HOLDER.get();
        return u == null ? null : u.userId();
    }
}
