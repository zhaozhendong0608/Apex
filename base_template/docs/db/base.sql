-- ==============================================================================
-- 🏛️ 全量系统底座 SQL 脚本 (base.sql)
-- 说明：
-- 1. 存放通用后台管理平台底座表结构 (sys_user, sys_role, sys_menu, sys_user_role, sys_role_menu)。
-- 2. 任何新业务项目均可直接复用本底座脚本拉起基础设施。
-- ==============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 1. 系统用户表 sys_user
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(50) NOT NULL COMMENT '登录账号',
  `password` varchar(100) NOT NULL COMMENT 'Bcrypt密码哈希',
  `nickname` varchar(50) DEFAULT '' COMMENT '真实姓名/昵称',
  `phone` varchar(20) DEFAULT '' COMMENT '联系手机',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '账号状态 (1:正常, 0:禁用)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_phone` (`phone`)
) ENGINE=InnoDB AUTO_INCREMENT=1001 DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- ----------------------------
-- 2. 系统角色表 sys_role
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_role` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '角色主键ID',
  `role_name` varchar(50) NOT NULL COMMENT '角色名称',
  `role_code` varchar(50) NOT NULL COMMENT '角色编码',
  `sort` int(11) NOT NULL DEFAULT '0' COMMENT '排序值',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '状态 (1:启用, 0:停用)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='系统角色表';

-- ----------------------------
-- 3. 系统菜单与权限表 sys_menu
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_menu` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '菜单主键ID',
  `parent_id` bigint(20) NOT NULL DEFAULT '0' COMMENT '父菜单ID',
  `menu_name` varchar(50) NOT NULL COMMENT '菜单/按钮名称',
  `menu_type` char(1) NOT NULL DEFAULT 'C' COMMENT '节点类型 (M:目录, C:菜单, F:按钮)',
  `path` varchar(200) DEFAULT '' COMMENT '路由路径',
  `component` varchar(255) DEFAULT '' COMMENT '前端组件路径',
  `permission` varchar(100) DEFAULT '' COMMENT '权限标识编码',
  `sort` int(11) NOT NULL DEFAULT '0' COMMENT '排序值',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='系统菜单权限表';

-- ----------------------------
-- 4. 用户-角色关联表 sys_user_role
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_user_role` (
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `role_id` bigint(20) NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`user_id`, `role_id`),
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- ----------------------------
-- 5. 角色-菜单关联表 sys_role_menu
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_role_menu` (
  `role_id` bigint(20) NOT NULL COMMENT '角色ID',
  `menu_id` bigint(20) NOT NULL COMMENT '菜单ID',
  PRIMARY KEY (`role_id`, `menu_id`),
  KEY `idx_menu_id` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单关联表';

-- ----------------------------
-- 6. 初始化超级管理员默认数据 (默认 admin / 密码 RawPassword123!)
-- ----------------------------
INSERT IGNORE INTO `sys_user` (`id`, `username`, `password`, `nickname`, `phone`, `status`) 
VALUES (1001, 'admin', '$2a$10$7rX.HkX0L9Q8m.hF8V.4u.6l2X5W.qV8/P2Z/J.Y3L/8d8.0Y.N6S', '超级管理员', '13800138000', 1);

INSERT IGNORE INTO `sys_role` (`id`, `role_name`, `role_code`, `sort`, `status`) 
VALUES (1, '超级管理员', 'admin', 1, 1), (2, '普通用户', 'common', 2, 1);

INSERT IGNORE INTO `sys_user_role` (`user_id`, `role_id`) 
VALUES (1001, 1);

SET FOREIGN_KEY_CHECKS = 1;
