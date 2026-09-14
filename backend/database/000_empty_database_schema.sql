-- MindFlow v1.0: structure only; use ONLY in a verified new empty database.
-- No user records or credentials included. Do not use mysql --force.
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for cognitive_revision
-- ----------------------------
CREATE TABLE `cognitive_revision` (
  `user_id` bigint NOT NULL,
  `revision` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for conversation
-- ----------------------------
CREATE TABLE `conversation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `source_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_platform` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'IMPORTED',
  `message_count` int NOT NULL DEFAULT '0',
  `content_hash` char(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_conversation_user_hash` (`user_id`,`content_hash`),
  KEY `idx_conversation_user_created` (`user_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Table structure for conversation_message
-- ----------------------------
CREATE TABLE `conversation_message` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `conversation_id` bigint NOT NULL,
  `sequence_no` int NOT NULL,
  `role` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `original_created_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_conversation_message_sequence` (`conversation_id`,`sequence_no`),
  KEY `idx_conversation_message_conversation` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Table structure for domain
-- ----------------------------
CREATE TABLE `domain` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `cognitive_summary` text COLLATE utf8mb4_unicode_ci,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_domain_user_name` (`user_id`,`name`),
  KEY `idx_domain_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Table structure for draft
-- ----------------------------
CREATE TABLE `draft` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '草稿ID',
  `user_id` bigint NOT NULL COMMENT '所属用户',
  `conversation_id` bigint DEFAULT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '草稿标题',
  `source_content` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `summary` text COLLATE utf8mb4_unicode_ci COMMENT 'AI总结（用户可修改）',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'review' COMMENT 'analyzing / review / completed',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `request_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_draft_request_id` (`request_id`),
  KEY `fk_draft_user` (`user_id`),
  KEY `idx_draft_conversation_id` (`conversation_id`),
  CONSTRAINT `fk_draft_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='认知草稿';

-- ----------------------------
-- Table structure for draft_commit_guard
-- ----------------------------
CREATE TABLE `draft_commit_guard` (
  `user_id` bigint NOT NULL,
  `draft_request_id` varchar(100) NOT NULL,
  `applied_plan_id` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`,`draft_request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for evolution
-- ----------------------------
CREATE TABLE `evolution` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '演化事件ID',
  `user_id` bigint NOT NULL COMMENT '所属用户',
  `source_draft_id` bigint DEFAULT NULL COMMENT '触发该演化的草稿，可为空',
  `step_order` int DEFAULT NULL COMMENT '该草稿中的思考步骤顺序',
  `event_type` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'QUESTION / FEATURE / CONCEPT / INSIGHT...',
  `title` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '演化事件标题',
  `content` text COLLATE utf8mb4_unicode_ci COMMENT '演化事件详细内容',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_evolution_draft_step` (`source_draft_id`,`step_order`),
  KEY `fk_evolution_user` (`user_id`),
  CONSTRAINT `fk_evolution_source_draft` FOREIGN KEY (`source_draft_id`) REFERENCES `draft` (`id`),
  CONSTRAINT `fk_evolution_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户认知演化轨迹';

-- ----------------------------
-- Table structure for evolution_knowledge
-- ----------------------------
CREATE TABLE `evolution_knowledge` (
  `evolution_id` bigint NOT NULL COMMENT '演化事件ID',
  `knowledge_id` bigint NOT NULL COMMENT '知识ID',
  PRIMARY KEY (`evolution_id`,`knowledge_id`),
  KEY `fk_evolution_knowledge_node` (`knowledge_id`),
  CONSTRAINT `fk_evolution_knowledge_evolution` FOREIGN KEY (`evolution_id`) REFERENCES `evolution` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_evolution_knowledge_node` FOREIGN KEY (`knowledge_id`) REFERENCES `knowledge_node` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='认知演化与知识节点的关联';

-- ----------------------------
-- Table structure for integration_plan
-- ----------------------------
CREATE TABLE `integration_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `draft_id` bigint NOT NULL,
  `draft_request_id` varchar(100) NOT NULL,
  `plan_request_id` varchar(100) NOT NULL,
  `status` varchar(24) NOT NULL,
  `baseline_revision` bigint NOT NULL,
  `confirmed_draft_json` longtext NOT NULL,
  `cognitive_update_json` longtext,
  `preview_json` longtext,
  `summary_json` longtext,
  `result_json` longtext,
  `expires_at` datetime NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_integration_plan_user_request` (`user_id`,`plan_request_id`),
  KEY `idx_integration_plan_user_status` (`user_id`,`status`),
  KEY `idx_integration_plan_draft` (`draft_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for knowledge_domain
-- ----------------------------
CREATE TABLE `knowledge_domain` (
  `knowledge_id` bigint NOT NULL,
  `domain_id` bigint NOT NULL,
  PRIMARY KEY (`knowledge_id`,`domain_id`),
  KEY `idx_knowledge_domain_domain_id` (`domain_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Table structure for knowledge_node
-- ----------------------------
CREATE TABLE `knowledge_node` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '知识ID',
  `user_id` bigint NOT NULL COMMENT '所属用户',
  `title` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '知识名称',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '知识内容（用户可编辑）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `fk_knowledge_user` (`user_id`),
  CONSTRAINT `fk_knowledge_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识节点';

-- ----------------------------
-- Table structure for knowledge_relation
-- ----------------------------
CREATE TABLE `knowledge_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '关系ID',
  `user_id` bigint NOT NULL COMMENT '所属用户',
  `source_draft_id` bigint DEFAULT NULL COMMENT '关系形成时来源的草稿，可为空',
  `description` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '为什么这些知识之间存在这种关系',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `fk_relation_user` (`user_id`),
  KEY `fk_relation_source_draft` (`source_draft_id`),
  CONSTRAINT `fk_relation_source_draft` FOREIGN KEY (`source_draft_id`) REFERENCES `draft` (`id`),
  CONSTRAINT `fk_relation_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识关系';

-- ----------------------------
-- Table structure for relation_knowledge
-- ----------------------------
CREATE TABLE `relation_knowledge` (
  `relation_id` bigint NOT NULL COMMENT '关系ID',
  `knowledge_id` bigint NOT NULL COMMENT '知识ID',
  PRIMARY KEY (`relation_id`,`knowledge_id`),
  KEY `fk_relation_knowledge_node` (`knowledge_id`),
  CONSTRAINT `fk_relation_knowledge_node` FOREIGN KEY (`knowledge_id`) REFERENCES `knowledge_node` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_relation_knowledge_relation` FOREIGN KEY (`relation_id`) REFERENCES `knowledge_relation` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='关系与知识节点的多对多关联';

-- ----------------------------
-- Table structure for users
-- ----------------------------
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户名',
  `email` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '邮箱',
  `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码（加密）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

SET FOREIGN_KEY_CHECKS = 1;
