-- ==============================================================================
-- 🚀 待执行增量 SQL 脚本 (next_execute_sql.sql)
-- 说明：
-- 本文件用于调整一级系统菜单层级顺序并同步权限：
--   1. 工作流工艺资产 (ID: 40, sort: 1) - 排在最顶端
--   2. 需求治理平台   (ID: 30, sort: 2) - 业务需求中心
--   3. 仿真实验平台   (ID: 20, sort: 3) - 网络拓扑实验
--   4. 系统管理       (ID: 10, sort: 4) - 系统底座与权限 (垫底)
-- ==============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ------------------------------------------------------------------------------
-- 1. 更新一级目录排序 (工作流 -> 需求 -> 仿真 -> 系统管理)
-- ------------------------------------------------------------------------------
-- 1.1 一级目录: 工作流工艺资产中心 (排序: 1)
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `sort`)
VALUES (40, 0, '工作流工艺资产', 'M', '/workflow-center', '', 'workflow:platform:view', 1)
ON DUPLICATE KEY UPDATE `menu_name` = VALUES(`menu_name`), `path` = VALUES(`path`), `sort` = 1;

-- 1.2 一级目录: 需求治理平台 (排序: 2)
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `sort`)
VALUES (30, 0, '需求治理平台', 'M', '/req', '', 'req:platform:view', 2)
ON DUPLICATE KEY UPDATE `menu_name` = VALUES(`menu_name`), `path` = VALUES(`path`), `sort` = 2;

-- 1.3 一级目录: 仿真实验平台 (排序: 3)
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `sort`)
VALUES (20, 0, '仿真实验平台', 'M', '/modules', '', 'sim:platform', 3)
ON DUPLICATE KEY UPDATE `menu_name` = VALUES(`menu_name`), `path` = VALUES(`path`), `sort` = 3;

-- 1.4 一级目录: 系统管理 (排序: 4 - 垫底)
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `sort`)
VALUES (10, 0, '系统管理', 'M', '/system', '', 'sys:manage', 4)
ON DUPLICATE KEY UPDATE `menu_name` = VALUES(`menu_name`), `path` = VALUES(`path`), `sort` = 4;

-- ------------------------------------------------------------------------------
-- 2. 确保工作流工艺资产下的二级菜单结构与最新挂载
-- ------------------------------------------------------------------------------
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `sort`)
VALUES
  (401, 40, '工作流模板治理', 'C', '/req/templates', 'modules/req-management/views/ReqManagementView', 'workflow:templates:view', 1),
  (402, 40, 'AI Skills 专家中心', 'C', '/req/skills', 'modules/req-management/views/ReqManagementView', 'workflow:skills:view', 2),
  (403, 40, '自动化脚本中心', 'C', '/req/scripts', 'modules/req-management/views/ReqManagementView', 'workflow:scripts:view', 3)
ON DUPLICATE KEY UPDATE `menu_name` = VALUES(`menu_name`), `parent_id` = VALUES(`parent_id`), `path` = VALUES(`path`), `component` = VALUES(`component`), `permission` = VALUES(`permission`), `sort` = VALUES(`sort`);

-- 清理旧需求治理平台下的遗留模板项
DELETE FROM `sys_role_menu` WHERE `menu_id` = 311;
DELETE FROM `sys_menu` WHERE `id` = 311;

-- ------------------------------------------------------------------------------
-- 3. 授予超级管理员(role_id=1)对应菜单权限
-- ------------------------------------------------------------------------------
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
VALUES
  (1, 40), (1, 401), (1, 402), (1, 403);

SET FOREIGN_KEY_CHECKS = 1;

-- ==============================================================================
-- ✅ 执行提示：请在 MySQL 终端中全选执行本脚本完成菜单层级排序更新
-- ==============================================================================
