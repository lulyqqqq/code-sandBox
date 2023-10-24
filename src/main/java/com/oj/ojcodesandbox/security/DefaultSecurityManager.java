package com.oj.ojcodesandbox.security;

import java.security.Permission;

/**
 * @ClassName: DefaultSecurityManager
 * 默认安全管理器
 * @author: mafangnian
 * @date: 2023/10/16 21:47
 */
public class DefaultSecurityManager extends SecurityManager {
    // 检查所有的权限
    @Override
    public void checkPermission(Permission perm) {
        throw new SecurityException("权限不足"+ perm.getActions());
    }
}
