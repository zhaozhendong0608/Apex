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

SET FOREIGN_KEY_CHECKS = 1;
