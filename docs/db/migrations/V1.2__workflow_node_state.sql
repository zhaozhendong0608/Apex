-- ------------------------------------------------------------------------------
-- 数据库升级脚本: V1.2__workflow_node_state.sql
-- 功能: 落地工作流节点工位真实执行状态表，支持按需求、轨道、工位三元组隔离
-- ------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `t_req_workflow_node_state` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `req_code` VARCHAR(64) NOT NULL COMMENT '需求编号 (如 REQ-001)',
  `track_mode` VARCHAR(32) NOT NULL DEFAULT 'STANDARD' COMMENT '所属轨道 (STANDARD/FASTTRACK)',
  `node_id` VARCHAR(64) NOT NULL COMMENT '工位算子ID (如 SOP-00, SOP-01, SOP-B2)',
  `node_name` VARCHAR(128) DEFAULT NULL COMMENT '工位算子名称快照',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '工位状态 (PENDING:未开始, IN_PROGRESS:进行中, COMPLETED:已完成, SKIPPED:跳过)',
  `operator` VARCHAR(64) DEFAULT NULL COMMENT '最后操作/责任人',
  `completed_at` DATETIME DEFAULT NULL COMMENT '完工时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_req_track_node` (`req_code`, `track_mode`, `node_id`),
  KEY `idx_req_code` (`req_code`),
  KEY `idx_track_mode` (`track_mode`),
  KEY `idx_node_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='需求管理-工作流节点工位真实执行状态表';

-- ------------------------------------------------------------------------------
-- 为现有需求 (REQ-001) 初始化演示工位状态数据 (根据现有 SOP 体系)
-- ------------------------------------------------------------------------------
INSERT IGNORE INTO `t_req_workflow_node_state` (`req_code`, `track_mode`, `node_id`, `node_name`, `status`, `operator`, `completed_at`) VALUES
('REQ-001', 'STANDARD', 'SOP-00', '体系总览', 'COMPLETED', '架构师', NOW()),
('REQ-001', 'STANDARD', 'SOP-01', '需求规划', 'IN_PROGRESS', '张工', NULL),
('REQ-001', 'STANDARD', 'SOP-02', '方案设计', 'PENDING', NULL, NULL),
('REQ-001', 'STANDARD', 'SOP-03', '任务拆解', 'PENDING', NULL, NULL),
('REQ-001', 'STANDARD', 'SOP-04', '单测先行', 'PENDING', NULL, NULL),
('REQ-001', 'STANDARD', 'SOP-05', '契约编码', 'PENDING', NULL, NULL),
('REQ-001', 'STANDARD', 'SOP-06', '集成联调', 'PENDING', NULL, NULL),
('REQ-001', 'STANDARD', 'SOP-07', '严格终验', 'PENDING', NULL, NULL),
('REQ-001', 'STANDARD', 'SOP-08', '上线部署', 'PENDING', NULL, NULL),
('REQ-001', 'STANDARD', 'SOP-09', '代码合流', 'PENDING', NULL, NULL);

-- 初始化快轨 (FASTTRACK) 骨架
INSERT IGNORE INTO `t_req_workflow_node_state` (`req_code`, `track_mode`, `node_id`, `node_name`, `status`, `operator`, `completed_at`) VALUES
('REQ-001', 'FASTTRACK', 'SOP-B1', '老代码考古', 'PENDING', NULL, NULL),
('REQ-001', 'FASTTRACK', 'SOP-B2', '极速微创', 'PENDING', NULL, NULL),
('REQ-001', 'FASTTRACK', 'SOP-B3', '小黄鸭排错', 'PENDING', NULL, NULL),
('REQ-001', 'FASTTRACK', 'SOP-B4', '故障急救', 'PENDING', NULL, NULL),
('REQ-001', 'FASTTRACK', 'SOP-B5', '记忆归档', 'PENDING', NULL, NULL),
('REQ-001', 'FASTTRACK', 'SOP-B6', '断点续传', 'PENDING', NULL, NULL);

