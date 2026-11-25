package com.rebuild.phoebe.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.rebuild.phoebe.common.UserContext;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 操作用户。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pb_user")
public class User extends BaseEntity {
    private Long accountId;
    private String username;
    @JsonIgnore
    private String passwordHash;
    private String displayName;
    private UserContext.Role role;
    private String status;
}
