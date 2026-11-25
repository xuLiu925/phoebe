package com.rebuild.phoebe.web;

import com.rebuild.phoebe.common.UserContext;
import org.junit.jupiter.api.AfterEach;

/** 测试辅助：模拟登录上下文。演示数据：账户1=demo(青禾)，账户2=demo2(悦颜)，账户3=demo3(小树)。 */
public abstract class TestSupport {

    protected void asAdvertiser(long accountId) {
        UserContext.set(new UserContext.CurrentUser(100L + accountId, "test" + accountId, UserContext.Role.ADVERTISER, accountId));
    }

    protected void asAuditor() {
        UserContext.set(new UserContext.CurrentUser(2L, "auditor", UserContext.Role.AUDITOR, null));
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }
}
