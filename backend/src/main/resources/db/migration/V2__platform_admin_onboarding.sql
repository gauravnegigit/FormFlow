-- ==============================================================================
-- FormFlow: V2 Platform-Controlled Admin Onboarding Migration (MySQL 8+)
-- Preserves all existing organizations, users, forms, and submissions data.
-- ==============================================================================

-- 1. Allow SUPER_ADMIN role and make organization_id nullable for platform-level accounts
ALTER TABLE `users`
    MODIFY COLUMN `role` ENUM('SUPER_ADMIN', 'ADMIN', 'USER', 'REVIEWER') NOT NULL,
    MODIFY COLUMN `organization_id` BINARY(16) NULL;

-- 2. Single-use hashed admin invitation tokens tied to one organization
CREATE TABLE `admin_invitations` (
    `id`              BINARY(16)  NOT NULL,
    `organization_id` BINARY(16)  NOT NULL,
    `created_by`      BINARY(16)  NOT NULL,
    `token_hash`      VARCHAR(64) NOT NULL,
    `expires_at`      DATETIME(6) NOT NULL,
    `used_at`         DATETIME(6) NULL,
    `created_at`      DATETIME(6) NOT NULL,

    PRIMARY KEY (`id`),
    CONSTRAINT `uk_admin_invitations_token_hash` UNIQUE (`token_hash`),
    CONSTRAINT `fk_admin_invitations_organization`
        FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`)
        ON DELETE RESTRICT,
    CONSTRAINT `fk_admin_invitations_created_by`
        FOREIGN KEY (`created_by`) REFERENCES `users` (`id`)
        ON DELETE RESTRICT,

    INDEX `idx_admin_invitations_org` (`organization_id`),
    INDEX `idx_admin_invitations_expires` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
