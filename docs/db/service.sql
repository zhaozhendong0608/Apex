-- ==============================================================================
-- 🧩 业务与扩展功能 SQL 脚本 (service.sql)
-- 说明：
-- 1. 本文件专门用于存放扩展功能与业务领域表（如站内消息通知、日志审计、业务订单等）。
-- 2. 与底座 base.sql 彻底解耦，按需开启加载。
-- ==============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 1. 站内消息通知表 sys_notify (扩展功能)
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_notify` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `title` varchar(100) NOT NULL COMMENT '通知标题',
  `content` text COMMENT '通知正文',
  `type` varchar(20) DEFAULT 'NOTICE' COMMENT '类型 (NOTICE:公告, MSG:站内信)',
  `publisher_id` bigint(20) DEFAULT NULL COMMENT '发布人ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内消息通知表';

-- ----------------------------
-- 2. 系统操作日志审计表 sys_log (扩展功能)
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) DEFAULT NULL COMMENT '操作人ID',
  `module` varchar(50) DEFAULT '' COMMENT '功能模块',
  `action` varchar(100) DEFAULT '' COMMENT '操作行为',
  `ip` varchar(50) DEFAULT '' COMMENT '操作IP',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统操作日志审计表';

-- ----------------------------
-- 3. 网络拓扑仿真表 net_topology (核心业务模块: module-network-sim)
-- ----------------------------
CREATE TABLE IF NOT EXISTS `net_topology` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '拓扑主键自增ID',
  `name` VARCHAR(128) NOT NULL COMMENT '拓扑方案名称',
  `description` VARCHAR(512) DEFAULT NULL COMMENT '拓扑详细描述',
  `canvas_data` LONGTEXT NOT NULL COMMENT 'AntV X6 前端序列化画布图元数据 (JSON)',
  `node_count` INT DEFAULT 0 COMMENT '设备节点总数量',
  `edge_count` INT DEFAULT 0 COMMENT '物理与逻辑链路总数',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='网络拓扑图元存储与仿真方案表';

-- ----------------------------
-- 插入初始默认演示拓扑种子数据 (HostA -> SW1 -> FW1 -> ServerB)
-- ----------------------------
INSERT INTO `net_topology` (`id`, `name`, `description`, `canvas_data`, `node_count`, `edge_count`)
VALUES (
  1,
  '企业标准DMZ与数据库拓扑 (Demo)',
  '典型办公网经过核心交换机、边界ACL防火墙访问内网数据库服务器的拓扑方案',
  '{"cells":[{"id":"node_host_a","shape":"html","x":100,"y":150,"width":140,"height":70,"data":{"type":"HOST","name":"主机 A (PC-1)","ip":"192.168.1.10","subnet":"255.255.255.0","acls":[]}},{"id":"node_sw_1","shape":"html","x":300,"y":150,"width":140,"height":70,"data":{"type":"SWITCH","name":"核心交换机 SW-1","ip":"192.168.1.254","subnet":"255.255.255.0","acls":[]}},{"id":"node_fw_1","shape":"html","x":520,"y":150,"width":140,"height":70,"data":{"type":"FIREWALL","name":"网关防火墙 FW","ip":"192.168.1.1","subnet":"255.255.255.0","acls":[{"id":"acl_1","action":"DENY","src":"192.168.1.10","dest":"10.0.0.88","port":3306,"proto":"TCP","desc":"禁止主机直接访问生产数据库3306端口"},{"id":"acl_2","action":"PERMIT","src":"192.168.1.0/24","dest":"10.0.0.0/24","port":80,"proto":"TCP","desc":"允许办公网HTTP流量通行"}]}},{"id":"node_server_b","shape":"html","x":740,"y":150,"width":140,"height":70,"data":{"type":"HOST","name":"数据库服务器 B","ip":"10.0.0.88","subnet":"255.255.255.0","acls":[]}},{"id":"edge_1","shape":"edge","source":{"cell":"node_host_a"},"target":{"cell":"node_sw_1"}},{"id":"edge_2","shape":"edge","source":{"cell":"node_sw_1"},"target":{"cell":"node_fw_1"}},{"id":"edge_3","shape":"edge","source":{"cell":"node_fw_1"},"target":{"cell":"node_server_b"}}]}',
  4,
  3
) ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);


-- ==============================================================================
-- 4. 需求与版本全生命周期治理核心模块 (req-management - V1.0)
-- 来源脚本: docs/db/migrations/V1.0__init_req_management_schema.sql
-- 业务定位: 贯彻落实“管住需求·版本·原型三件事，做到留痕、可确认、可追溯”的核心事实源。
-- 包含核心表:
--   - t_req_project           : 项目空间与租户隔离表
--   - t_req_requirement       : 需求定义与客户原话不可篡改留痕表
--   - t_req_confirm_item      : 范围/规则/异常/验收/不做四层清单与实时代价表
--   - t_req_prototype_version : 原型版本托管、预览地址与全目录 SHA256 指纹快照表
--   - t_req_baseline          : 需求基线、Git Tag 与 Manifest 哈希存证表
--   - t_req_approval          : 客户免登三方法定签名凭证与防伪回执留痕表
--   - t_req_artifact          : 设计文档与原型归档产物挂载表
-- ==============================================================================

-- 4.1 项目空间表
CREATE TABLE IF NOT EXISTS `t_req_project` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `project_key` VARCHAR(32) NOT NULL COMMENT '项目唯一代号(大写英文字母数字,如NET-SIM)',
  `name` VARCHAR(128) NOT NULL COMMENT '项目名称',
  `client_name` VARCHAR(128) NOT NULL COMMENT '客户单位名称',
  `description` VARCHAR(512) DEFAULT NULL COMMENT '项目描述',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE活跃, ARCHIVED归档',
  `created_by` VARCHAR(64) NOT NULL DEFAULT 'system' COMMENT '创建人',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_req_proj_key` (`project_key`),
  KEY `idx_req_proj_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='需求管理-项目空间表';

-- 4.2 需求定义表 (含客户原话留痕)
CREATE TABLE IF NOT EXISTS `t_req_requirement` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `project_id` BIGINT NOT NULL COMMENT '关联项目ID',
  `req_code` VARCHAR(32) NOT NULL COMMENT '需求业务编码(如REQ-001)',
  `title` VARCHAR(256) NOT NULL COMMENT '需求标题',
  `raw_quote` TEXT NOT NULL COMMENT '客户原话(不可篡改留痕)',
  `trigger_event` VARCHAR(256) DEFAULT NULL COMMENT '业务触发背景/事件',
  `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT草稿, CLARIFYING澄清中, PENDING待确认, BASELINED已基线, RELEASED已发布, CLOSED已关闭',
  `priority` VARCHAR(16) NOT NULL DEFAULT 'P1' COMMENT '优先级: P1核心必做, P2重要, P3优化',
  `owner_name` VARCHAR(64) NOT NULL COMMENT '责任产品经理/研发',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_req_code` (`project_id`, `req_code`),
  KEY `idx_req_proj_status` (`project_id`, `status`),
  KEY `idx_req_owner` (`owner_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='需求管理-需求实体表';

-- 4.3 四层确认清单与代价表
CREATE TABLE IF NOT EXISTS `t_req_confirm_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `requirement_id` BIGINT NOT NULL COMMENT '关联需求ID',
  `category` VARCHAR(32) NOT NULL COMMENT '清单四层分类: SCOPE范围, RULE规则, EXCEPTION异常, ACCEPTANCE验收, SKIP本期不做',
  `item_code` VARCHAR(32) NOT NULL COMMENT '条目代号(如R-01, B-01, N-01)',
  `question` VARCHAR(512) NOT NULL COMMENT '业务问法/约定项标题',
  `options_json` JSON NOT NULL COMMENT '选项定义与候选代价(JSON数组)',
  `decision` VARCHAR(32) NOT NULL DEFAULT 'UNDECIDED' COMMENT '决策结果: CURRENT本期做, NEXT下期做, SKIP不做, UNDECIDED未定',
  `cost_days` DECIMAL(5,2) NOT NULL DEFAULT '0.00' COMMENT '该决策产生的人天代价',
  `decided_by` VARCHAR(64) DEFAULT NULL COMMENT '最终确认人',
  `decided_at` DATETIME DEFAULT NULL COMMENT '确认决策时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_req_item_code` (`requirement_id`, `item_code`),
  KEY `idx_item_req_cat` (`requirement_id`, `category`),
  KEY `idx_item_decision` (`decision`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='需求管理-四层确认清单表';

-- 4.4 原型版本托管与快照表
CREATE TABLE IF NOT EXISTS `t_req_prototype_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `requirement_id` BIGINT NOT NULL COMMENT '关联需求ID',
  `version_no` VARCHAR(32) NOT NULL COMMENT '原型版本标识(如v1, v2, v3)',
  `dir_path` VARCHAR(256) NOT NULL COMMENT '原型解压静态托管相对路径',
  `file_hash` VARCHAR(64) NOT NULL COMMENT '整个原型目录SHA256哈希指纹',
  `preview_url` VARCHAR(512) NOT NULL COMMENT '在线预览只读地址(/preview/...)',
  `change_summary` VARCHAR(512) DEFAULT NULL COMMENT '本次版本主要变更说明',
  `uploaded_by` VARCHAR(64) NOT NULL COMMENT '上传人',
  `uploaded_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_proto_ver` (`requirement_id`, `version_no`),
  KEY `idx_proto_req` (`requirement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='需求管理-原型版本快照表';

-- 4.5 需求基线表 (不可变存证)
CREATE TABLE IF NOT EXISTS `t_req_baseline` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `project_id` BIGINT NOT NULL COMMENT '关联项目ID',
  `requirement_id` BIGINT NOT NULL COMMENT '关联需求ID',
  `version` VARCHAR(32) NOT NULL COMMENT '基线版本号(如BL-v1.0, BL-v1.1)',
  `git_tag` VARCHAR(128) NOT NULL COMMENT '不可篡改Git Tag(如bl/NET-SIM/REQ-001/v1.0)',
  `prototype_version_id` BIGINT NOT NULL COMMENT '锁定的原型版本ID',
  `manifest_path` VARCHAR(256) NOT NULL COMMENT 'manifest.json存储路径',
  `manifest_hash` VARCHAR(64) NOT NULL COMMENT 'manifest文件SHA256校验和',
  `archive_zip_path` VARCHAR(256) NOT NULL COMMENT '完整归档Zip存储路径',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态: DRAFT草案, PENDING待签, BASELINED已冻结, SUPERSEDED已替代',
  `total_cost_days` DECIMAL(6,2) NOT NULL DEFAULT '0.00' COMMENT '锁定基线时的共识总人天',
  `frozen_at` DATETIME DEFAULT NULL COMMENT '正式冻结时间',
  `superseded_by` VARCHAR(32) DEFAULT NULL COMMENT '被哪个后续基线替代',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_base_ver` (`requirement_id`, `version`),
  KEY `idx_base_proj` (`project_id`),
  KEY `idx_base_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='需求管理-需求基线表';

-- 4.6 客户免登三方法定签名凭证表
CREATE TABLE IF NOT EXISTS `t_req_approval` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `baseline_id` BIGINT NOT NULL COMMENT '关联基线ID',
  `role_type` VARCHAR(32) NOT NULL COMMENT '签署角色: BIZ_OWNER业务拍板人, ACCEPTOR验收人, USER_REP使用者代表',
  `signer_name` VARCHAR(64) NOT NULL COMMENT '签署人实名',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING待签署, SIGNED已签署, REJECTED已驳回',
  `token` VARCHAR(64) NOT NULL COMMENT '免登录一次性加密Token',
  `access_code` VARCHAR(8) NOT NULL COMMENT '4位访问验证码',
  `token_expires_at` DATETIME NOT NULL COMMENT 'Token有效截止时间',
  `signed_at` DATETIME DEFAULT NULL COMMENT '签署提交时间',
  `signer_ip` VARCHAR(45) DEFAULT NULL COMMENT '签署终端IP',
  `user_agent` VARCHAR(512) DEFAULT NULL COMMENT '签署客户端浏览器UA',
  `restatement_text` TEXT DEFAULT NULL COMMENT '客户一句话复述理解存证',
  `receipt_code` VARCHAR(64) DEFAULT NULL COMMENT '电子回执校验指纹码',
  `reject_reason` TEXT DEFAULT NULL COMMENT '若驳回时填写的异议分歧说明',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_appr_token` (`token`),
  KEY `idx_appr_baseline_role` (`baseline_id`, `role_type`),
  KEY `idx_appr_receipt` (`receipt_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='需求管理-客户签名凭证表';

-- 4.7 设计文档与归档产物表
CREATE TABLE IF NOT EXISTS `t_req_artifact` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `project_id` BIGINT NOT NULL COMMENT '关联项目ID',
  `requirement_id` BIGINT NOT NULL COMMENT '关联需求ID',
  `baseline_id` BIGINT DEFAULT NULL COMMENT '挂载基线ID(基线冻结时锁死)',
  `type` VARCHAR(32) NOT NULL COMMENT '产物类型: TECH_DESIGN技术方案, ARCH_DESIGN架构概要, DETAIL_DESIGN详细设计, PROTO_ZIP原型包',
  `doc_name` VARCHAR(256) NOT NULL COMMENT '文档原始名称',
  `version_tag` VARCHAR(32) NOT NULL COMMENT '文档版本标识(如v1.0)',
  `storage_path` VARCHAR(512) NOT NULL COMMENT '对象存储/文件系统存储路径',
  `sha256_hash` VARCHAR(64) NOT NULL COMMENT '文件SHA256哈希指纹',
  `file_size` BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小(字节)',
  `uploaded_by` VARCHAR(64) NOT NULL COMMENT '上传人',
  `uploaded_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  PRIMARY KEY (`id`),
  KEY `idx_art_req` (`requirement_id`),
  KEY `idx_art_baseline` (`baseline_id`),
  KEY `idx_art_hash` (`sha256_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='需求管理-设计文档与归档产物表';

-- ------------------------------------------------------------------------------
-- 4.8 需求管理模块演示种子数据 (NET-SIM 项目空间, REQ-001 需求, 四层清单与 BL-v1.0 基线)
-- ------------------------------------------------------------------------------
INSERT IGNORE INTO `t_req_project` (`id`, `project_key`, `name`, `client_name`, `description`, `status`, `created_by`)
VALUES (1, 'NET-SIM', '全景工业网络仿真与拓扑排版系统', '国家电网调度中心', '大型工业互联网与变电站网络拓扑仿真及等保隔离可视化平台', 'ACTIVE', 'admin');

INSERT IGNORE INTO `t_req_requirement` (`id`, `project_id`, `req_code`, `title`, `raw_quote`, `trigger_event`, `status`, `priority`, `owner_name`)
VALUES (1, 1, 'REQ-001', '需求与版本全生命周期治理核心模块', '必须管住需求、版本、原型三件事，做到留痕、可确认、可追溯。每次变更都要客户免登签字，复述理解，绝不允许口头扯皮。', '项目中期范围频繁蔓延与客户验收扯皮', 'BASELINED', 'P1', '张工');

INSERT IGNORE INTO `t_req_confirm_item` (`id`, `requirement_id`, `category`, `item_code`, `question`, `options_json`, `decision`, `cost_days`, `decided_by`)
VALUES
  (1, 1, 'SCOPE', 'R-01', '在线状态与告警监视实时看板', '[{"label":"全部实时推送","cost":1.5},{"label":"仅轮询刷新","cost":0.8}]', 'CURRENT', 1.50, '王工'),
  (2, 1, 'RULE', 'B-01', '心跳掉线判定阈值严格设定为60秒', '[{"label":"60秒","cost":0.5},{"label":"120秒","cost":0.2}]', 'CURRENT', 0.50, '王工'),
  (3, 1, 'EXCEPTION', 'E-01', '网络中断本地自动降级环形缓存', '[{"label":"本地SQLite缓冲","cost":1.0},{"label":"内存抛弃","cost":0.0}]', 'CURRENT', 1.00, '王工'),
  (4, 1, 'ACCEPTANCE', 'A-01', '三方免登录移动端/PC端防伪签署回执', '[{"label":"HMAC一次性令牌签署","cost":1.0}]', 'CURRENT', 1.00, '王工'),
  (5, 1, 'SKIP', 'N-01', '历史轨迹长周期全景3D回放', '[{"label":"本期不做","cost":0.0}]', 'SKIP', 0.00, '王工'),
  (6, 1, 'SKIP', 'N-02', '跨数据中心异地多活毫秒级数据同步', '[{"label":"延期下期做","cost":0.0}]', 'NEXT', 0.00, '王工'),
  (7, 1, 'SKIP', 'N-03', '第三方ERP财务账单直接结算接口', '[{"label":"本期不做","cost":0.0}]', 'SKIP', 0.00, '王工');

INSERT IGNORE INTO `t_req_prototype_version` (`id`, `requirement_id`, `version_no`, `dir_path`, `file_hash`, `preview_url`, `change_summary`, `uploaded_by`)
VALUES (1, 1, 'v1', 'REQ-1/v1', 'a3f9c2e177b9d40e1189ac3bb4109401bfd145cba2793108c40b7190e82c7a10', '/static/prototypes/REQ-1/v1/mockup.html', '初始高保真交互原型：P01~P13 完整时间线、四层清单决策与 E01~E03 客户免登签署', 'admin');

INSERT IGNORE INTO `t_req_baseline` (`id`, `project_id`, `requirement_id`, `version`, `git_tag`, `prototype_version_id`, `manifest_path`, `manifest_hash`, `archive_zip_path`, `status`, `total_cost_days`, `frozen_at`)
VALUES (1, 1, 1, 'BL-v1.0', 'bl/NET-SIM/REQ-001/v1.0', 1, 'storage/baselines/NET-SIM/REQ-001/BL-v1.0/manifest.json', 'a3f9c2e177b9d40e1189ac3bb4109401bfd145cba2793108c40b7190e82c7a10', 'storage/baselines/NET-SIM/REQ-001/BL-v1.0/REQ-001-BL-v1.0.zip', 'BASELINED', 4.00, '2026-10-29 14:00:00');

INSERT IGNORE INTO `t_req_approval` (`id`, `baseline_id`, `role_type`, `signer_name`, `status`, `token`, `access_code`, `token_expires_at`, `signed_at`, `signer_ip`, `user_agent`, `restatement_text`, `receipt_code`)
VALUES
  (1, 1, 'BIZ_OWNER', '王总监', 'SIGNED', 'demo-token-biz-owner-64chars-long-hash-token-value-0000000000000001', '7892', '2026-11-29 23:59:59', '2026-10-29 14:02:11', '127.0.0.1', 'Mozilla/5.0 Chrome/120.0', '这版做在线状态和告警，不做历史曲线和财务导出，符合预期。', '7F3A-91C2-88B1'),
  (2, 1, 'ACCEPTOR', '李科长', 'SIGNED', 'demo-token-acceptor-64chars-long-hash-token-value-0000000000000002', '7892', '2026-11-29 23:59:59', '2026-10-29 14:05:00', '127.0.0.1', 'Mozilla/5.0 Chrome/120.0', '确认四层清单指标，以此为终验基准。', '8B2C-44A1-90E2'),
  (3, 1, 'USER_REP', '赵工', 'SIGNED', 'demo-token-user-rep-64chars-long-hash-token-value-0000000000000003', '7892', '2026-11-29 23:59:59', '2026-10-29 14:08:23', '127.0.0.1', 'Mozilla/5.0 Chrome/120.0', '操作流符合现场调度人员使用习惯。', '1C9E-78D3-55F4');


-- ------------------------------------------------------------------------------
-- 4.9 需求治理平台动态菜单与权限配置 (sys_menu & sys_role_menu)
-- ------------------------------------------------------------------------------
-- 一级目录: 需求治理平台 (ID: 30)
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `sort`)
VALUES (30, 0, '需求治理平台', 'M', '/req', '', 'req:platform:view', 3)
ON DUPLICATE KEY UPDATE `menu_name` = VALUES(`menu_name`), `path` = VALUES(`path`), `sort` = VALUES(`sort`);

-- 二级菜单: 项目空间与全局配置菜单 (挂载于需求治理平台 ID: 30 之下)
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `sort`)
VALUES
  (301, 30, '项目工作台', 'C', '/req/dashboard', 'modules/req-management/views/ReqManagementView', 'req:dashboard:view', 1),
  -- 302: 需求详情下钻页 (由工作台列表点击进入，不作为左侧常驻导航展示)
  (302, 30, '需求详情 & 清单', 'C', '/req/detail', 'modules/req-management/views/ReqManagementView', 'req:detail:view', 2),
  (303, 30, '原型版本与托管', 'C', '/req/prototypes', 'modules/req-management/views/ReqManagementView', 'req:prototypes:view', 3),
  (304, 30, '需求基线与签字', 'C', '/req/baselines', 'modules/req-management/views/ReqManagementView', 'req:baselines:view', 4),
  (305, 30, '基线差异对比', 'C', '/req/baseline-diff', 'modules/req-management/views/ReqManagementView', 'req:diff:view', 5),
  (306, 30, '发布版本追踪', 'C', '/req/releases', 'modules/req-management/views/ReqManagementView', 'req:releases:view', 6),
  (307, 30, '需求演进时间线', 'C', '/req/timeline', 'modules/req-management/views/ReqManagementView', 'req:timeline:view', 7),
  (308, 30, '码表配置中心', 'C', '/req/enums', 'modules/req-management/views/ReqManagementView', 'req:enums:view', 8),
  (309, 30, '外观与工业主题', 'C', '/req/theme', 'modules/req-management/views/ReqManagementView', 'req:theme:view', 9)
ON DUPLICATE KEY UPDATE `menu_name` = VALUES(`menu_name`), `parent_id` = VALUES(`parent_id`), `path` = VALUES(`path`), `component` = VALUES(`component`), `permission` = VALUES(`permission`), `sort` = VALUES(`sort`);

-- 授予超级管理员(role_id=1)全量需求治理菜单权限
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
VALUES
  (1, 30),
  (1, 301),
  (1, 302),
  (1, 303),
  (1, 304),
  (1, 305),
  (1, 306),
  (1, 307),
  (1, 308),
  (1, 309);

SET FOREIGN_KEY_CHECKS = 1;
