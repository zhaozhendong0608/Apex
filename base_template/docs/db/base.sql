-- ==============================================================================
-- 🏛️ 全量基线 SQL 脚本 (base.sql)
-- 
-- 说明：
-- 1. 本文件用于存放框架/系统底座全量表结构（如用户表、角色表、菜单表、字典表、系统日志表等）。
-- 2. 新环境（Dev/Test）拉起或开箱即用初始化时，直接执行本脚本完成整个底座初始化。
-- 3. 伴随大版本 Release 发布（SOP-04/05），已验证通过的增量 V1.x SQL 需合并反哺更新至本文件。
-- ==============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 示例：系统用户表 sys_user
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(100) NOT NULL COMMENT '密码哈希',
  `nickname` varchar(50) DEFAULT '' COMMENT '昵称',
  `email` varchar(100) DEFAULT '' COMMENT '邮箱',
  `phone` varchar(20) DEFAULT '' COMMENT '手机号',
  `status` tinyint(4) DEFAULT '1' COMMENT '状态 (1:正常, 0:禁用)',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

SET FOREIGN_KEY_CHECKS = 1;
