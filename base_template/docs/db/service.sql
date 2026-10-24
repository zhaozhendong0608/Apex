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

SET FOREIGN_KEY_CHECKS = 1;
