-- ==============================================================================
-- FormFlow: Clean MySQL 8+ Database Schema Initialization Script
-- Generated strictly from current JPA entities, enums, and repositories
-- Target database: formflow_db
-- UUID representation: BINARY(16) (Hibernate sequential MSB/LSB: UUID_TO_BIN(..., 0))
-- ==============================================================================

CREATE DATABASE IF NOT EXISTS `formflow_db`
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE `formflow_db`;

-- ------------------------------------------------------------------------------
-- 1. organizations (Entity: Organization.java)
-- ------------------------------------------------------------------------------
CREATE TABLE `organizations` (
    `id`          BINARY(16)   NOT NULL,
    `name`        VARCHAR(150) NOT NULL,
    `description` TEXT         NULL,
    `created_at`  DATETIME(6)  NOT NULL,
    `updated_at`  DATETIME(6)  NOT NULL,

    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 2. users (Entity: User.java | Enum: UserRole.java)
-- ------------------------------------------------------------------------------
CREATE TABLE `users` (
    `id`              BINARY(16)                        NOT NULL,
    `name`            VARCHAR(100)                      NOT NULL,
    `email`           VARCHAR(255)                      NOT NULL,
    `password`        VARCHAR(255)                      NOT NULL,
    `role`            ENUM('ADMIN', 'USER', 'REVIEWER') NOT NULL,
    `organization_id` BINARY(16)                        NOT NULL,
    `created_at`      DATETIME(6)                       NOT NULL,
    `updated_at`      DATETIME(6)                       NOT NULL,

    PRIMARY KEY (`id`),
    CONSTRAINT `uk_users_email` UNIQUE (`email`),
    CONSTRAINT `fk_users_organization`
        FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`)
        ON DELETE RESTRICT,

    INDEX `idx_users_org_role` (`organization_id`, `role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 3. forms (Entity: Form.java | Enum: FormStatus.java)
-- ------------------------------------------------------------------------------
CREATE TABLE `forms` (
    `id`              BINARY(16)                           NOT NULL,
    `title`           VARCHAR(255)                         NOT NULL,
    `description`     TEXT                                 NULL,
    `form_schema`     JSON                                 NOT NULL,
    `status`          ENUM('DRAFT', 'PUBLISHED', 'CLOSED') NOT NULL DEFAULT 'DRAFT',
    `public_slug`     VARCHAR(255)                         NULL,
    `organization_id` BINARY(16)                           NOT NULL,
    `created_by`      BINARY(16)                           NOT NULL,
    `created_at`      DATETIME(6)                          NOT NULL,
    `updated_at`      DATETIME(6)                          NOT NULL,

    PRIMARY KEY (`id`),
    CONSTRAINT `uk_forms_public_slug` UNIQUE (`public_slug`),
    CONSTRAINT `fk_forms_organization`
        FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`)
        ON DELETE RESTRICT,
    CONSTRAINT `fk_forms_created_by`
        FOREIGN KEY (`created_by`) REFERENCES `users` (`id`)
        ON DELETE RESTRICT,

    INDEX `idx_forms_organization` (`organization_id`),
    INDEX `idx_forms_created_by` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 4. submissions (Entity: Submission.java | Enum: SubmissionStatus.java)
-- Note: Uses assigned_to exclusively; legacy assigned_reviewer_id is omitted.
-- ------------------------------------------------------------------------------
CREATE TABLE `submissions` (
    `id`               BINARY(16)                             NOT NULL,
    `organization_id`  BINARY(16)                             NOT NULL,
    `form_id`          BINARY(16)                             NOT NULL,
    `answers`          JSON                                   NOT NULL,
    `status`           ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
    `tracking_token`   VARCHAR(255)                           NOT NULL,
    `category`         VARCHAR(100)                           NULL,
    `assigned_to`      BINARY(16)                             NULL,
    `assigned_at`      DATETIME(6)                            NULL,
    `submitted_at`     DATETIME(6)                            NOT NULL,
    `reviewed_at`      DATETIME(6)                            NULL,
    `rejection_reason` TEXT                                   NULL,
    `duplicate_flag`   BIT(1)                                 NOT NULL DEFAULT 0,
    `verification`     JSON                                   NULL,
    `created_at`       DATETIME(6)                            NOT NULL,
    `updated_at`       DATETIME(6)                            NOT NULL,

    PRIMARY KEY (`id`),
    CONSTRAINT `uk_submissions_tracking_token` UNIQUE (`tracking_token`),
    CONSTRAINT `fk_submissions_organization`
        FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`)
        ON DELETE RESTRICT,
    CONSTRAINT `fk_submissions_form`
        FOREIGN KEY (`form_id`) REFERENCES `forms` (`id`)
        ON DELETE RESTRICT,
    CONSTRAINT `fk_submissions_assigned_to`
        FOREIGN KEY (`assigned_to`) REFERENCES `users` (`id`)
        ON DELETE SET NULL,

    INDEX `idx_submissions_org_form` (`organization_id`, `form_id`),
    INDEX `idx_submissions_org_assigned` (`organization_id`, `assigned_to`),
    INDEX `idx_submissions_form` (`form_id`),
    INDEX `idx_submissions_assigned_to` (`assigned_to`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
