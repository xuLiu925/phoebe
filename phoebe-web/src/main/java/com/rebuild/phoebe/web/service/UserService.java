package com.rebuild.phoebe.web.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rebuild.phoebe.common.BizException;
import com.rebuild.phoebe.common.ErrorCode;
import com.rebuild.phoebe.common.UserContext;
import com.rebuild.phoebe.domain.dto.AccountRequest;
import com.rebuild.phoebe.domain.entity.Account;
import com.rebuild.phoebe.domain.entity.User;
import com.rebuild.phoebe.domain.mapper.UserMapper;
import com.rebuild.phoebe.domain.service.AccountService;
import com.rebuild.phoebe.web.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserMapper userMapper;
    private final AccountService accountService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public Map<String, Object> login(String username, String password) {
        User u = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (u == null || !"ACTIVE".equals(u.getStatus()) || !passwordEncoder.matches(password, u.getPasswordHash())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        UserContext.CurrentUser cu = new UserContext.CurrentUser(u.getId(), u.getUsername(), u.getRole(), u.getAccountId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("token", jwtService.issue(cu));
        m.put("user", u);
        return m;
    }

    public Map<String, Object> me() {
        UserContext.CurrentUser cu = UserContext.get();
        User u = userMapper.selectById(cu.userId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("user", u);
        m.put("actingAccountId", cu.accountId());
        m.put("account", cu.accountId() == null ? null : accountService.get(cu.accountId()));
        return m;
    }

    /** 管理员开户：创建广告账户 + 广告主登录账号。 */
    @Transactional
    public Map<String, Object> createAdvertiser(AccountRequest req) {
        if (req.getUsername() == null || req.getUsername().isBlank() || req.getPassword() == null || req.getPassword().length() < 6) {
            throw BizException.badRequest("请填写登录用户名和不少于6位的密码");
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername())) > 0) {
            throw BizException.badRequest("用户名已存在");
        }
        Account a = accountService.create(req);
        User u = new User();
        u.setAccountId(a.getId());
        u.setUsername(req.getUsername().trim());
        u.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        u.setDisplayName(a.getCompanyName());
        u.setRole(UserContext.Role.ADVERTISER);
        u.setStatus("ACTIVE");
        userMapper.insert(u);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("account", a);
        m.put("user", u);
        return m;
    }

    public List<User> users() {
        return userMapper.selectList(new LambdaQueryWrapper<User>().orderByAsc(User::getId));
    }
}
