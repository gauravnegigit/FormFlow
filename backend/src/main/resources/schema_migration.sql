-- ==============================================================================
-- FormFlow: Multi-Tenancy & Submissions Schema Migration Script (MySQL)
-- Safe, Idempotent, and Non-Destructive Migration Procedure (Final Revision)
-- ==============================================================================
-- 
-- IMPORTANT ARCHITECTURAL & DATA NOTES:
-- 1. LEGACY DATA MULTI-TENANCY SCOPE:
--    All pre-existing users, forms, and submissions are backfilled into a single
--    system organization ('Default Organization', UUID: 00000000-0000-0000-0000-000000000001).
--    This ensures legacy data is not orphaned and foreign keys can be safely enforced
--    as NOT NULL. Because existing database records share this default organization,
--    they are initially co-located in the same tenant scope until reallocated.
--    Newly registered organizations and their users/forms are strictly isolated.
--
-- 2. UUID STORAGE CONVENTION:
--    All primary and relationship UUID columns in the database use BINARY(16).
--    Hibernate 6 maps java.util.UUID on MySQL to BINARY(16) using standard sequential
--    byte order (8 bytes MSB followed by 8 bytes LSB).
--    In MySQL, UUID_TO_BIN('...', 0) (without the swap flag) produces the exact
--    matching 16-byte representation. Do NOT use UUID_TO_BIN('...', 1).
--
-- 3. CROSS-ORGANIZATION REVIEWER ASSIGNMENT VALIDATION:
--    The foreign key constraint `fk_submissions_assigned_to` ensures that `assigned_to`
--    points to a valid user in `users(id)`. Standard relational foreign keys CANNOT
--    enforce tenant isolation across tables (i.e. guaranteeing that submission.organization_id
--    matches reviewer.organization_id and that the user's role is REVIEWER).
--    This tenant boundary validation is strictly enforced in the Java application layer
--    inside `SubmissionService.assignReviewer(UUID submissionId, UUID reviewerId)`.
--
-- 4. PRESERVATION OF EXISTING COLUMNS:
--    - `submissions.assigned_reviewer_id` and its existing foreign key to `users.id`
--      are preserved and NOT dropped.
--    - `submissions.assigned_at` already exists as DATETIME(6) NULL and is NOT re-added.
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- STEP 1: Create 'organizations' table if it does not exist
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `organizations` (
    `id` BINARY(16) NOT NULL,
    `name` VARCHAR(150) NOT NULL,
    `description` TEXT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- STEP 2: Insert Default Organization for legacy data backfill
-- UUID: 00000000-0000-0000-0000-000000000001 (Sequential BINARY(16) representation)
-- ------------------------------------------------------------------------------
INSERT IGNORE INTO `organizations` (`id`, `name`, `description`, `created_at`, `updated_at`)
VALUES (
    UUID_TO_BIN('00000000-0000-0000-0000-000000000001', 0),
    'Default Organization',
    'Shared system organization for legacy accounts prior to multi-tenancy migration',
    NOW(6),
    NOW(6)
);

-- ------------------------------------------------------------------------------
-- STEP 3: Update 'users' table
-- ------------------------------------------------------------------------------
-- 3.1. Expand 'role' ENUM to include 'REVIEWER' alongside 'ADMIN' and 'USER'
ALTER TABLE `users` MODIFY COLUMN `role` ENUM('ADMIN', 'USER', 'REVIEWER') NOT NULL DEFAULT 'ADMIN';

-- 3.2. Add column organization_id as NULL if not present
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'organization_id');
SET @query := IF(@col_exists = 0, 'ALTER TABLE `users` ADD COLUMN `organization_id` BINARY(16) NULL', 'SELECT 1');
PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3.3. Backfill existing users without an organization to the default organization
UPDATE `users` 
SET `organization_id` = UUID_TO_BIN('00000000-0000-0000-0000-000000000001', 0) 
WHERE `organization_id` IS NULL;

-- 3.4. Enforce NOT NULL on users.organization_id after backfilling
ALTER TABLE `users` MODIFY COLUMN `organization_id` BINARY(16) NOT NULL;

-- 3.5. Idempotently create Foreign Key fk_users_organization
SET @fk_exists := (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS 
    WHERE TABLE_SCHEMA = DATABASE() 
      AND TABLE_NAME = 'users' 
      AND CONSTRAINT_NAME = 'fk_users_organization' 
      AND CONSTRAINT_TYPE = 'FOREIGN KEY');
SET @query := IF(@fk_exists = 0, 
    'ALTER TABLE `users` ADD CONSTRAINT `fk_users_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`) ON DELETE RESTRICT', 
    'SELECT 1');
PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------------------------
-- STEP 4: Update 'forms' table
-- ------------------------------------------------------------------------------
-- 4.1. Add column organization_id as NULL if not present
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'forms' AND COLUMN_NAME = 'organization_id');
SET @query := IF(@col_exists = 0, 'ALTER TABLE `forms` ADD COLUMN `organization_id` BINARY(16) NULL', 'SELECT 1');
PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4.2. Backfill forms: inherit organization from creator, or fallback to default organization
UPDATE `forms` f 
JOIN `users` u ON f.created_by = u.id 
SET f.organization_id = u.organization_id 
WHERE f.organization_id IS NULL;

UPDATE `forms` 
SET `organization_id` = UUID_TO_BIN('00000000-0000-0000-0000-000000000001', 0) 
WHERE `organization_id` IS NULL;

-- 4.3. Enforce NOT NULL on forms.organization_id after backfilling
ALTER TABLE `forms` MODIFY COLUMN `organization_id` BINARY(16) NOT NULL;

-- 4.4. Idempotently create Foreign Key fk_forms_organization
SET @fk_exists := (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS 
    WHERE TABLE_SCHEMA = DATABASE() 
      AND TABLE_NAME = 'forms' 
      AND CONSTRAINT_NAME = 'fk_forms_organization' 
      AND CONSTRAINT_TYPE = 'FOREIGN KEY');
SET @query := IF(@fk_exists = 0, 
    'ALTER TABLE `forms` ADD CONSTRAINT `fk_forms_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`) ON DELETE RESTRICT', 
    'SELECT 1');
PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------------------------
-- STEP 5: Update 'submissions' table
-- ------------------------------------------------------------------------------
-- 5.1. Add column organization_id as NULL
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'submissions' AND COLUMN_NAME = 'organization_id');
SET @query := IF(@col_exists = 0, 'ALTER TABLE `submissions` ADD COLUMN `organization_id` BINARY(16) NULL', 'SELECT 1');
PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5.2. Add column category (optional)
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'submissions' AND COLUMN_NAME = 'category');
SET @query := IF(@col_exists = 0, 'ALTER TABLE `submissions` ADD COLUMN `category` VARCHAR(100) NULL', 'SELECT 1');
PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5.3. Add column assigned_to (optional ManyToOne User)
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'submissions' AND COLUMN_NAME = 'assigned_to');
SET @query := IF(@col_exists = 0, 'ALTER TABLE `submissions` ADD COLUMN `assigned_to` BINARY(16) NULL', 'SELECT 1');
PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5.4. Preserve legacy assigned_reviewer_id and copy existing values into assigned_to if necessary
-- Note: assigned_reviewer_id and its FK are intentionally retained and NOT dropped.
UPDATE `submissions` 
SET `assigned_to` = `assigned_reviewer_id` 
WHERE `assigned_to` IS NULL AND `assigned_reviewer_id` IS NOT NULL;

-- 5.5. submissions.assigned_at already exists in the database as DATETIME(6) NULL; do NOT re-add.

-- 5.6. Add column reviewed_at (DATETIME(6) NULL)
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'submissions' AND COLUMN_NAME = 'reviewed_at');
SET @query := IF(@col_exists = 0, 'ALTER TABLE `submissions` ADD COLUMN `reviewed_at` DATETIME(6) NULL', 'SELECT 1');
PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5.7. Add column rejection_reason (TEXT NULL)
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'submissions' AND COLUMN_NAME = 'rejection_reason');
SET @query := IF(@col_exists = 0, 'ALTER TABLE `submissions` ADD COLUMN `rejection_reason` TEXT NULL', 'SELECT 1');
PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5.8. Add column duplicate_flag (BIT(1) NOT NULL DEFAULT 0)
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'submissions' AND COLUMN_NAME = 'duplicate_flag');
SET @query := IF(@col_exists = 0, 'ALTER TABLE `submissions` ADD COLUMN `duplicate_flag` BIT(1) NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5.9. Add column verification (JSON NULL)
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'submissions' AND COLUMN_NAME = 'verification');
SET @query := IF(@col_exists = 0, 'ALTER TABLE `submissions` ADD COLUMN `verification` JSON NULL', 'SELECT 1');
PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5.10. Add column created_at (DATETIME(6) NULL initially for backfill)
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'submissions' AND COLUMN_NAME = 'created_at');
SET @query := IF(@col_exists = 0, 'ALTER TABLE `submissions` ADD COLUMN `created_at` DATETIME(6) NULL', 'SELECT 1');
PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5.11. Add column updated_at (DATETIME(6) NULL initially for backfill)
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'submissions' AND COLUMN_NAME = 'updated_at');
SET @query := IF(@col_exists = 0, 'ALTER TABLE `submissions` ADD COLUMN `updated_at` DATETIME(6) NULL', 'SELECT 1');
PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5.12. Backfill submissions.organization_id from associated form
UPDATE `submissions` s 
JOIN `forms` f ON s.form_id = f.id 
SET s.organization_id = f.organization_id 
WHERE s.organization_id IS NULL;

UPDATE `submissions` 
SET `organization_id` = UUID_TO_BIN('00000000-0000-0000-0000-000000000001', 0) 
WHERE `organization_id` IS NULL;

-- 5.13. Backfill created_at and updated_at from submitted_at if null
UPDATE `submissions` 
SET `created_at` = `submitted_at` 
WHERE `created_at` IS NULL;

UPDATE `submissions` 
SET `updated_at` = `submitted_at` 
WHERE `updated_at` IS NULL;

-- 5.14. Enforce NOT NULL on submissions required columns after backfill
-- Note: assigned_to, assigned_at, reviewed_at, rejection_reason, and verification remain NULLable.
ALTER TABLE `submissions` MODIFY COLUMN `organization_id` BINARY(16) NOT NULL;
ALTER TABLE `submissions` MODIFY COLUMN `created_at` DATETIME(6) NOT NULL;
ALTER TABLE `submissions` MODIFY COLUMN `updated_at` DATETIME(6) NOT NULL;

-- 5.15. Idempotently create Foreign Key fk_submissions_organization
SET @fk_exists := (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS 
    WHERE TABLE_SCHEMA = DATABASE() 
      AND TABLE_NAME = 'submissions' 
      AND CONSTRAINT_NAME = 'fk_submissions_organization' 
      AND CONSTRAINT_TYPE = 'FOREIGN KEY');
SET @query := IF(@fk_exists = 0, 
    'ALTER TABLE `submissions` ADD CONSTRAINT `fk_submissions_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`) ON DELETE RESTRICT', 
    'SELECT 1');
PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5.16. Idempotently create Foreign Key fk_submissions_assigned_to
SET @fk_exists := (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS 
    WHERE TABLE_SCHEMA = DATABASE() 
      AND TABLE_NAME = 'submissions' 
      AND CONSTRAINT_NAME = 'fk_submissions_assigned_to' 
      AND CONSTRAINT_TYPE = 'FOREIGN KEY');
SET @query := IF(@fk_exists = 0, 
    'ALTER TABLE `submissions` ADD CONSTRAINT `fk_submissions_assigned_to` FOREIGN KEY (`assigned_to`) REFERENCES `users` (`id`) ON DELETE SET NULL', 
    'SELECT 1');
PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ==============================================================================
-- POST-MIGRATION VERIFICATION QUERIES
-- Run these queries after applying the migration to verify schema integrity.
-- ==============================================================================

-- Verification 1: Verify users.role ENUM includes ADMIN, USER, and REVIEWER
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT 
FROM information_schema.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'role';

-- Verification 2: Verify all new and modified columns across tables
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT 
FROM information_schema.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND (
    (TABLE_NAME = 'organizations') OR
    (TABLE_NAME = 'users' AND COLUMN_NAME IN ('organization_id', 'role')) OR
    (TABLE_NAME = 'forms' AND COLUMN_NAME = 'organization_id') OR
    (TABLE_NAME = 'submissions' AND COLUMN_NAME IN (
        'organization_id', 'category', 'assigned_to', 'assigned_reviewer_id',
        'assigned_at', 'reviewed_at', 'rejection_reason', 'duplicate_flag',
        'verification', 'created_at', 'updated_at'
    ))
  )
ORDER BY TABLE_NAME, ORDINAL_POSITION;

-- Verification 3: Verify foreign key constraints are present
SELECT CONSTRAINT_NAME, TABLE_NAME, CONSTRAINT_TYPE 
FROM information_schema.TABLE_CONSTRAINTS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND CONSTRAINT_NAME IN (
      'fk_users_organization',
      'fk_forms_organization',
      'fk_submissions_organization',
      'fk_submissions_assigned_to'
  );

-- Verification 4: Verify zero NULLs in NOT NULL backfilled columns
SELECT 
    (SELECT COUNT(*) FROM `users` WHERE `organization_id` IS NULL) AS null_user_orgs,
    (SELECT COUNT(*) FROM `forms` WHERE `organization_id` IS NULL) AS null_form_orgs,
    (SELECT COUNT(*) FROM `submissions` WHERE `organization_id` IS NULL) AS null_sub_orgs,
    (SELECT COUNT(*) FROM `submissions` WHERE `created_at` IS NULL) AS null_sub_created_at,
    (SELECT COUNT(*) FROM `submissions` WHERE `updated_at` IS NULL) AS null_sub_updated_at;

-- Verification 5: Verify reviewer assignment migration consistency
-- Both counts should match, and unmigrated count should be 0
SELECT 
    COUNT(*) AS total_submissions,
    COUNT(`assigned_reviewer_id`) AS legacy_assigned_count,
    COUNT(`assigned_to`) AS migrated_assigned_count,
    SUM(CASE WHEN `assigned_reviewer_id` IS NOT NULL AND `assigned_to` IS NULL THEN 1 ELSE 0 END) AS unmigrated_count,
    SUM(CASE WHEN `assigned_reviewer_id` IS NOT NULL AND `assigned_to` != `assigned_reviewer_id` THEN 1 ELSE 0 END) AS mismatched_assignments
FROM `submissions`;

-- Verification 6: Verify organization record count and referential integrity
SELECT 
    o.id AS organization_binary_id,
    BIN_TO_UUID(o.id) AS organization_uuid,
    o.name,
    COUNT(DISTINCT u.id) AS user_count,
    COUNT(DISTINCT f.id) AS form_count,
    COUNT(DISTINCT s.id) AS submission_count
FROM `organizations` o
LEFT JOIN `users` u ON u.organization_id = o.id
LEFT JOIN `forms` f ON f.organization_id = o.id
LEFT JOIN `submissions` s ON s.organization_id = o.id
GROUP BY o.id, o.name;
