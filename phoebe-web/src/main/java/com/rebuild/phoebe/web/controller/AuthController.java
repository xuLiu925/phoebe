package com.rebuild.phoebe.web.controller;

import com.rebuild.phoebe.common.Result;
import com.rebuild.phoebe.domain.dto.AccountRequest;
import com.rebuild.phoebe.domain.entity.Account;
import com.rebuild.phoebe.domain.entity.User;
import com.rebuild.phoebe.domain.service.AccountService;
import com.rebuild.phoebe.common.UserContext;
import com.rebuild.phoebe.domain.config.AuditProperties;
import com.rebuild.phoebe.web.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Tag(name = "认证与账户")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;
    private final AccountService accountService;
    private final AuditProperties auditProperties;

    public record LoginRequest(String username, String password) {
    }

    @Operation(summary = "登录，返回 JWT")
    @PostMapping("/auth/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest req) {
        return Result.ok(userService.login(req.username(), req.password()));
    }

    @Operation(summary = "当前用户与账户（管理员代登录时为被代理账户）")
    @GetMapping("/auth/me")
    public Result<Map<String, Object>> me() {
        return Result.ok(userService.me());
    }

    @Operation(summary = "当前广告账户")
    @GetMapping("/account")
    public Result<Account> account() {
        return Result.ok(accountService.get(UserContext.requireAccountId()));
    }

    @Operation(summary = "[管理员] 账户列表")
    @GetMapping("/admin/accounts")
    public Result<List<Account>> accounts() {
        return Result.ok(accountService.list());
    }

    @Operation(summary = "[管理员] 用户列表")
    @GetMapping("/admin/users")
    public Result<List<User>> users() {
        return Result.ok(userService.users());
    }

    @Operation(summary = "[管理员] 开户：创建广告账户与广告主账号（行业准入校验）")
    @PostMapping("/admin/accounts")
    public Result<Map<String, Object>> createAccount(@Valid @RequestBody AccountRequest req) {
        return Result.ok(userService.createAdvertiser(req));
    }

    public record RechargeRequest(BigDecimal amount) {
    }

    @Operation(summary = "[管理员] 模拟充值")
    @PostMapping("/admin/accounts/{id}/recharge")
    public Result<Account> recharge(@PathVariable Long id, @RequestBody RechargeRequest req) {
        return Result.ok(accountService.recharge(id, req.amount()));
    }

    public record AuditModeRequest(boolean autoApprove) {
    }

    @Operation(summary = "[管理员] 切换模拟审核模式（自动通过/人工审核），运行期生效")
    @PutMapping("/admin/audit-mode")
    public Result<AuditProperties> auditMode(@RequestBody AuditModeRequest req) {
        auditProperties.setAutoApprove(req.autoApprove());
        return Result.ok(auditProperties);
    }
}
