-- ==============================================================================
-- 增量更新执行脚本 (Next Execute SQL)
-- 适用范围：已执行过基础表结构的数据库实例，安全增量应用本次修改
-- 包含内容：
--   1. 修改工作流模板表字段注释（去除旧品牌名称）
--   2. 更新默认工作流模板种子数据 (TPL-DEFAULT 名称及描述更新为 AI辅助四层一体化研发工作流)
--   3. 更新一级目录与二级菜单（工作流工艺资产独立与菜单重排，幂等更新）
--   4. 补全超级管理员角色菜单权限映射 (sys_role_menu)
-- ==============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ------------------------------------------------------------------------------
-- 1. 更新表字段注释 (t_req_workflow_template)
-- ------------------------------------------------------------------------------
ALTER TABLE `t_req_workflow_template` 
  MODIFY COLUMN `name` VARCHAR(128) NOT NULL COMMENT '模板名称 (如 全局默认·AI辅助四层一体化研发工作流)';

-- ------------------------------------------------------------------------------
-- 2. 更新系统默认工作流模板种子数据 (TPL-DEFAULT)
-- ------------------------------------------------------------------------------
UPDATE `t_req_workflow_template`
SET 
  `name` = '全局默认 · AI辅助四层一体化研发工作流',
  `description` = 'Apex 标准生产工艺：4-Layer 全景拓扑（SOP指令轨 + AI Skill专家轨 + 控制中枢轨 + 确定性物理产物轨），支持双轨延迟汇合与 B2 旁路微创快轨。',
  `updated_at` = NOW()
WHERE `template_code` = 'TPL-DEFAULT' OR `id` = 1;

-- ------------------------------------------------------------------------------
-- 3. 动态菜单与排序配置 (sys_menu，支持 ON DUPLICATE KEY UPDATE 幂等执行)
-- 菜单排序规则 (Sort Order):
--   1. 工作流工艺资产 (/workflow-center, ID: 40) - 顶层全局规范与工艺资产
--   2. 需求治理平台 (/req, ID: 30) - 业务需求流转与项目研发
--   3. 仿真实验平台 (/modules, ID: 20) - 业务仿真网络拓扑实验
--   4. 系统管理 (/system, ID: 10) - 底座开户与权限管理 (垫底)
-- ------------------------------------------------------------------------------

-- 3.1 工作流工艺资产中心 (一级目录 ID: 40, 排序: 1)
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `sort`)
VALUES (40, 0, '工作流工艺资产', 'M', '/workflow-center', '', 'workflow:platform:view', 1)
ON DUPLICATE KEY UPDATE `menu_name` = VALUES(`menu_name`), `path` = VALUES(`path`), `sort` = VALUES(`sort`);

-- 工作流二级菜单 (挂载于 ID: 40)
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `sort`)
VALUES
  (401, 40, '工作流模板治理', 'C', '/req/templates', 'modules/req-management/views/ReqManagementView', 'workflow:templates:view', 1),
  (402, 40, 'AI Skills 专家中心', 'C', '/req/skills', 'modules/req-management/views/ReqManagementView', 'workflow:skills:view', 2),
  (403, 40, '自动化脚本中心', 'C', '/req/scripts', 'modules/req-management/views/ReqManagementView', 'workflow:scripts:view', 3)
ON DUPLICATE KEY UPDATE `menu_name` = VALUES(`menu_name`), `parent_id` = VALUES(`parent_id`), `path` = VALUES(`path`), `component` = VALUES(`component`), `permission` = VALUES(`permission`), `sort` = VALUES(`sort`);

-- 3.2 需求治理平台 (一级目录 ID: 30, 排序: 2)
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `sort`)
VALUES (30, 0, '需求治理平台', 'M', '/req', '', 'req:platform:view', 2)
ON DUPLICATE KEY UPDATE `menu_name` = VALUES(`menu_name`), `path` = VALUES(`path`), `sort` = VALUES(`sort`);

-- 需求治理二级菜单 (挂载于 ID: 30)
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `sort`)
VALUES
  (300, 30, '项目列表', 'C', '/req/projects', 'modules/req-management/views/ReqManagementView', 'req:projects:view', 1),
  (310, 30, '工程流水线', 'C', '/req/workflow', 'modules/req-management/views/ReqManagementView', 'req:workflow:view', 2),
  (303, 30, '原型版本与托管', 'C', '/req/prototypes', 'modules/req-management/views/ReqManagementView', 'req:prototypes:view', 3),
  (306, 30, '发布版本追踪', 'C', '/req/releases', 'modules/req-management/views/ReqManagementView', 'req:releases:view', 4),
  (307, 30, '需求演进时间线', 'C', '/req/timeline', 'modules/req-management/views/ReqManagementView', 'req:timeline:view', 5),
  (308, 30, '码表配置中心', 'C', '/req/enums', 'modules/req-management/views/ReqManagementView', 'req:enums:view', 6),
  (309, 30, '外观与工业主题', 'C', '/req/theme', 'modules/req-management/views/ReqManagementView', 'req:theme:view', 7),
  -- 301/302/304/305: 下钻子页面 (由项目卡片或需求详情点击进入，前端不作为左侧侧边栏展示，但保留系统权限控制)
  (301, 30, '项目工作台', 'C', '/req/dashboard', 'modules/req-management/views/ReqManagementView', 'req:dashboard:view', 11),
  (302, 30, '需求详情 & 清单', 'C', '/req/detail', 'modules/req-management/views/ReqManagementView', 'req:detail:view', 12),
  (304, 30, '需求基线与签字', 'C', '/req/baselines', 'modules/req-management/views/ReqManagementView', 'req:baselines:view', 13),
  (305, 30, '基线差异对比', 'C', '/req/baseline-diff', 'modules/req-management/views/ReqManagementView', 'req:diff:view', 14)
ON DUPLICATE KEY UPDATE `menu_name` = VALUES(`menu_name`), `parent_id` = VALUES(`parent_id`), `path` = VALUES(`path`), `component` = VALUES(`component`), `permission` = VALUES(`permission`), `sort` = VALUES(`sort`);

-- 3.3 仿真实验平台 (一级目录 ID: 20, 排序: 3)
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `sort`)
VALUES (20, 0, '仿真实验平台', 'M', '/modules', '', 'sim:platform', 3)
ON DUPLICATE KEY UPDATE `menu_name` = VALUES(`menu_name`), `path` = VALUES(`path`), `sort` = VALUES(`sort`);

-- 3.4 系统管理 (一级目录 ID: 10, 排序: 4 - 垫底)
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `sort`)
VALUES (10, 0, '系统管理', 'M', '/system', '', 'sys:manage', 4)
ON DUPLICATE KEY UPDATE `menu_name` = VALUES(`menu_name`), `path` = VALUES(`path`), `sort` = VALUES(`sort`);

-- ------------------------------------------------------------------------------
-- 4. 授予超级管理员(role_id=1)全量菜单权限映射 (sys_role_menu)
-- ------------------------------------------------------------------------------
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
VALUES
  (1, 40), (1, 401), (1, 402), (1, 403),
  (1, 30), (1, 300), (1, 310), (1, 301), (1, 302), (1, 303), (1, 304), (1, 305), (1, 306), (1, 307), (1, 308), (1, 309),
  (1, 20), (1, 201), (1, 202),
  (1, 10), (1, 101), (1, 102);

SET FOREIGN_KEY_CHECKS = 1;

