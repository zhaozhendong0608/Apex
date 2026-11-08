-- ==============================================================================
-- 🚀 待执行增量 SQL 脚本 (next_execute_sql.sql)
-- 说明：
-- 完美兼容 MySQL 5.7 / 8.0 / MariaDB 全版本语法
-- 包含：
--   1. 项目表扩充 workflow_template_id 字段及索引 (存储过程幂等添加)
--   2. 新建工作流模板表 t_req_workflow_template
--   3. 插入全局唯一默认模板 (TPL-DEFAULT) 初始种子数据
--   4. 动态菜单与管理员角色权限配置
-- ==============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ------------------------------------------------------------------------------
-- 1. 项目空间表扩充 workflow_template_id 字段 (存储过程幂等安全执行)
-- ------------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_upgrade_apex_project_table`;
DELIMITER $$
CREATE PROCEDURE `sp_upgrade_apex_project_table`()
BEGIN
  -- 1.1 检查并添加 workflow_template_id 字段
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS 
    WHERE table_schema = DATABASE() 
      AND table_name = 't_req_project' 
      AND column_name = 'workflow_template_id'
  ) THEN
    ALTER TABLE `t_req_project` 
    ADD COLUMN `workflow_template_id` BIGINT DEFAULT 1 COMMENT '绑定的项目专属工作流模板ID(衍生自全局默认或定制模板)' AFTER `description`;
  END IF;

  -- 1.2 检查并添加对应索引
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.STATISTICS 
    WHERE table_schema = DATABASE() 
      AND table_name = 't_req_project' 
      AND index_name = 'idx_req_proj_wf_tpl'
  ) THEN
    ALTER TABLE `t_req_project` ADD INDEX `idx_req_proj_wf_tpl` (`workflow_template_id`);
  END IF;
END $$
DELIMITER ;

CALL `sp_upgrade_apex_project_table`();
DROP PROCEDURE IF EXISTS `sp_upgrade_apex_project_table`;


-- ------------------------------------------------------------------------------
-- 2. 新建工作流模板管理表 t_req_workflow_template
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_req_workflow_template` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `template_code` VARCHAR(64) NOT NULL COMMENT '模板代号 (如 TPL-DEFAULT, TPL-ENERGY)',
  `name` VARCHAR(128) NOT NULL COMMENT '模板名称 (如 全局默认·钧天四层一体化研发工作流)',
  `description` VARCHAR(512) DEFAULT NULL COMMENT '模板适用场景与工程规约说明',
  `is_system_default` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否系统唯一默认蓝本 (1:是, 0:否)',
  `scope_type` VARCHAR(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '作用域类型 (GLOBAL:全局公共, PROJECT:项目专属)',
  `project_id` BIGINT DEFAULT NULL COMMENT '关联项目ID (当 scope_type=PROJECT 时有效)',
  `project_key` VARCHAR(32) DEFAULT NULL COMMENT '关联项目代号快照',
  `nodes_config` LONGTEXT NOT NULL COMMENT '节点算子与四层元数据定义 (JSON格式)',
  `edges_config` LONGTEXT DEFAULT NULL COMMENT '拓扑连线与门禁流向定义 (JSON格式)',
  `created_by` VARCHAR(64) NOT NULL DEFAULT 'admin' COMMENT '创建人',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wf_tpl_code` (`template_code`),
  KEY `idx_wf_tpl_scope` (`scope_type`, `project_id`),
  KEY `idx_wf_tpl_default` (`is_system_default`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='需求管理-工作流模板与工位蓝本表';


-- ------------------------------------------------------------------------------
-- 3. 插入全局唯一初始默认工作流模板种子数据 (TPL-DEFAULT)
-- ------------------------------------------------------------------------------
INSERT INTO `t_req_workflow_template` (`id`, `template_code`, `name`, `description`, `is_system_default`, `scope_type`, `project_id`, `project_key`, `nodes_config`, `edges_config`, `created_by`)
VALUES (
  1,
  'TPL-DEFAULT',
  '全局默认 · 钧天四层一体化研发工作流',
  '钧天标准生产工艺：4-Layer 全景拓扑（SOP指令轨 + AI Skill专家轨 + 控制中枢轨 + 确定性物理产物轨），支持双轨延迟汇合与 B2 旁路微创快轨。',
  1,
  'GLOBAL',
  NULL,
  NULL,
  '[{"id":"SOP-00","name":"体系总览","role":"全员","skill":"-","sop":".ai/sop/00-sop-overview.md","gate":"自适应分流"},{"id":"SOP-01","name":"需求规划","role":"产品/张工","skill":"requirement-discovery","sop":".ai/sop/01-sop-planning.md","gate":"四层清单齐备"},{"id":"SOP-02","name":"方案设计","role":"研发/李工","skill":"design-spec-architect","sop":".ai/sop/02-sop-tech-design.md","gate":"python3 gate.py --sql"},{"id":"TC-GEN","name":"测试用例矩阵","role":"测试/王工","skill":"quality-verifier","sop":".ai/sop/01-sop-planning.md","gate":"用例覆盖100%"},{"id":"SOP-03","name":"TDD编码","role":"研发/李工","skill":"coding-standards","sop":".ai/sop/03-sop-coding.md","gate":"单元测试全绿"},{"id":"SOP-04","name":"机器门禁","role":"看门狗探针","skill":"code-quality-reviewer","sop":".ai/sop/04-sop-code-review.md","gate":"python3 gate.py --staged [LGTM]"},{"id":"SOP-05","name":"人工评审","role":"架构师/程工","skill":"code-quality-reviewer","sop":".ai/sop/05-sop-review.md","gate":"架构师签字核准"},{"id":"SOP-06","name":"集成测试","role":"测试/王工","skill":"quality-verifier","sop":".ai/sop/06-sop-integration.md","gate":"TC用例100%碰撞通过"},{"id":"SOP-07","name":"双向验收","role":"看门狗探针","skill":"quality-verifier","sop":".ai/sop/07-sop-acceptance.md","gate":"board.py verify放行"},{"id":"SOP-08","name":"容器部署","role":"运维/李工","skill":"-","sop":".ai/sop/08-sop-deploy.md","gate":"健康检查通过"},{"id":"SOP-09","name":"Git发布","role":"交付/张工","skill":"-","sop":".ai/sop/09-sop-git-release.md","gate":"Release资产落盘"},{"id":"SOP-B2","name":"极速修补","role":"研发/李工","skill":"quality-verifier","sop":".ai/sop/B2-sop-fasttrack.md","gate":"单文件≤20行"}]',
  '[]',
  'admin'
) ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `description` = VALUES(`description`);

-- 确保示例项目绑定该默认模板
UPDATE `t_req_project` SET `workflow_template_id` = 1 WHERE `project_key` = 'NET-SIM' AND `workflow_template_id` IS NULL;


-- ------------------------------------------------------------------------------
-- 4. 动态菜单与管理员角色权限增量配置
-- ------------------------------------------------------------------------------
-- 插入或更新工作流模板管理菜单 (ID: 311)
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `sort`)
VALUES
  (311, 30, '工作流模板管理', 'C', '/req/templates', 'modules/req-management/views/ReqManagementView', 'req:templates:view', 3)
ON DUPLICATE KEY UPDATE `menu_name` = VALUES(`menu_name`), `parent_id` = VALUES(`parent_id`), `path` = VALUES(`path`), `component` = VALUES(`component`), `permission` = VALUES(`permission`), `sort` = VALUES(`sort`);

-- 授予超级管理员 (role_id=1) 对应权限
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
VALUES (1, 311);

SET FOREIGN_KEY_CHECKS = 1;
