-- ==============================================================================
-- FormFlow: Phase 2 Database Hardening Migration Script (MySQL 8+)
-- Idempotent, Duplicate-Aware Schema & Index Hardening (Revised)
-- ==============================================================================
-- IMPORTANT:
-- - Back up `formflow_db` before executing this migration.
-- - Requires `formflow_db` to be the active database (`DATABASE() = 'formflow_db'`).
-- - Preserves all existing data; aborts legacy cleanup cleanly if any unmigrated
--   or divergent `assigned_reviewer_id` rows exist.
-- - Safe to re-run multiple times.
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- STEP 0: Require `formflow_db` to be selected before running anything
-- ------------------------------------------------------------------------------
SET @active_db := DATABASE();
SET @db_guard_sql := IF(
    @active_db = 'formflow_db',
    'SELECT ''Verified active database: formflow_db'' AS db_check',
    'SELECT * FROM `ERROR_ABORT_formflow_db_must_be_selected_before_running_migration`'
);
PREPARE stmt FROM @db_guard_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------------------------
-- STEP 1: Safely remove legacy `submissions.assigned_reviewer_id` & its FK
-- Rules:
--   1. Check unmigrated and divergent assignments BEFORE dropping anything.
--   2. Only drop the FK and column if BOTH counts are strictly zero.
--   3. If either count is nonzero, skip the entire cleanup (no partial changes)
--      and report the exact reason.
-- ------------------------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'formflow_db'
      AND TABLE_NAME = 'submissions'
      AND COLUMN_NAME = 'assigned_reviewer_id'
);

SET @unmigrated_count := 0;
SET @divergent_count := 0;

SET @audit_legacy_sql := IF(
    @col_exists > 0,
    'SELECT
        COALESCE(SUM(CASE WHEN `assigned_reviewer_id` IS NOT NULL AND `assigned_to` IS NULL THEN 1 ELSE 0 END), 0),
        COALESCE(SUM(CASE WHEN `assigned_reviewer_id` IS NOT NULL AND `assigned_to` IS NOT NULL AND `assigned_to` <> `assigned_reviewer_id` THEN 1 ELSE 0 END), 0)
     INTO @unmigrated_count, @divergent_count
     FROM `submissions`',
    'SELECT 0, 0 INTO @unmigrated_count, @divergent_count'
);
PREPARE stmt FROM @audit_legacy_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @can_cleanup_legacy := IF(@col_exists > 0 AND @unmigrated_count = 0 AND @divergent_count = 0, 1, 0);

SELECT CASE
    WHEN @col_exists = 0 THEN
        'SKIPPED: submissions.assigned_reviewer_id is already absent.'
    WHEN @unmigrated_count > 0 AND @divergent_count > 0 THEN
        CONCAT('SKIPPED ENTIRE CLEANUP: Found ', @unmigrated_count, ' unmigrated row(s) and ', @divergent_count, ' divergent row(s). Legacy FK and assigned_reviewer_id preserved untouched.')
    WHEN @unmigrated_count > 0 THEN
        CONCAT('SKIPPED ENTIRE CLEANUP: Found ', @unmigrated_count, ' unmigrated row(s) where assigned_reviewer_id IS NOT NULL and assigned_to IS NULL. Legacy FK and assigned_reviewer_id preserved untouched.')
    WHEN @divergent_count > 0 THEN
        CONCAT('SKIPPED ENTIRE CLEANUP: Found ', @divergent_count, ' divergent row(s) where assigned_to <> assigned_reviewer_id. Legacy FK and assigned_reviewer_id preserved untouched.')
    ELSE
        'PROCEEDING: Zero unmigrated and zero divergent rows verified. Dropping legacy FK and assigned_reviewer_id.'
END AS legacy_cleanup_status;

-- 1.1. Drop legacy FK only when @can_cleanup_legacy = 1
SET @legacy_fk := IF(
    @can_cleanup_legacy = 1,
    (
        SELECT CONSTRAINT_NAME
        FROM information_schema.KEY_COLUMN_USAGE
        WHERE TABLE_SCHEMA = 'formflow_db'
          AND TABLE_NAME = 'submissions'
          AND COLUMN_NAME = 'assigned_reviewer_id'
          AND REFERENCED_TABLE_NAME IS NOT NULL
        LIMIT 1
    ),
    NULL
);

SET @drop_fk_sql := IF(
    @can_cleanup_legacy = 1 AND @legacy_fk IS NOT NULL,
    CONCAT('ALTER TABLE `submissions` DROP FOREIGN KEY `', @legacy_fk, '`'),
    'SELECT ''No legacy FK dropped'' AS legacy_fk_action'
);
PREPARE stmt FROM @drop_fk_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Verify no additional FK still references submissions.assigned_reviewer_id before dropping column
SET @remaining_legacy_fks := (
    SELECT COUNT(*)
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = 'formflow_db'
      AND TABLE_NAME = 'submissions'
      AND COLUMN_NAME = 'assigned_reviewer_id'
      AND REFERENCED_TABLE_NAME IS NOT NULL
);

-- 1.2. Drop legacy column only when @can_cleanup_legacy = 1 and zero FKs remain on it
SET @drop_col_sql := IF(
    @can_cleanup_legacy = 1 AND @remaining_legacy_fks = 0,
    'ALTER TABLE `submissions` DROP COLUMN `assigned_reviewer_id`',
    'SELECT ''Legacy column submissions.assigned_reviewer_id not dropped'' AS legacy_col_action'
);
PREPARE stmt FROM @drop_col_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------------------------
-- STEP 2: Add missing `organization_id` Foreign Keys (`ON DELETE RESTRICT`)
-- Checks KEY_COLUMN_USAGE by (table, column, referenced_table, referenced_column)
-- so duplicate FKs are never created under different names.
-- ------------------------------------------------------------------------------

-- 2.1. users.organization_id -> organizations.id
SET @existing_fk := (
    SELECT CONSTRAINT_NAME
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = 'formflow_db'
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'organization_id'
      AND REFERENCED_TABLE_NAME = 'organizations'
      AND REFERENCED_COLUMN_NAME = 'id'
    LIMIT 1
);
SET @sql := IF(
    @existing_fk IS NULL,
    'ALTER TABLE `users` ADD CONSTRAINT `fk_users_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`) ON DELETE RESTRICT',
    CONCAT('SELECT ''SKIPPED: FK on users.organization_id -> organizations.id already exists (', @existing_fk, ')'' AS fk_users_status')
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2.2. forms.organization_id -> organizations.id
SET @existing_fk := (
    SELECT CONSTRAINT_NAME
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = 'formflow_db'
      AND TABLE_NAME = 'forms'
      AND COLUMN_NAME = 'organization_id'
      AND REFERENCED_TABLE_NAME = 'organizations'
      AND REFERENCED_COLUMN_NAME = 'id'
    LIMIT 1
);
SET @sql := IF(
    @existing_fk IS NULL,
    'ALTER TABLE `forms` ADD CONSTRAINT `fk_forms_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`) ON DELETE RESTRICT',
    CONCAT('SELECT ''SKIPPED: FK on forms.organization_id -> organizations.id already exists (', @existing_fk, ')'' AS fk_forms_status')
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2.3. submissions.organization_id -> organizations.id
SET @existing_fk := (
    SELECT CONSTRAINT_NAME
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = 'formflow_db'
      AND TABLE_NAME = 'submissions'
      AND COLUMN_NAME = 'organization_id'
      AND REFERENCED_TABLE_NAME = 'organizations'
      AND REFERENCED_COLUMN_NAME = 'id'
    LIMIT 1
);
SET @sql := IF(
    @existing_fk IS NULL,
    'ALTER TABLE `submissions` ADD CONSTRAINT `fk_submissions_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`) ON DELETE RESTRICT',
    CONCAT('SELECT ''SKIPPED: FK on submissions.organization_id -> organizations.id already exists (', @existing_fk, ')'' AS fk_submissions_status')
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------------------------
-- STEP 3: Create Composite Indexes (Name + Ordered Column Verification)
-- Rules:
--   1. Detect equivalent indexes by ordered column sequence (exact or leading prefix).
--   2. Never skip creating a required index if its target name exists with WRONG columns;
--      instead, warn and create the required ordered-column index with a `_v2` suffix.
-- ------------------------------------------------------------------------------

-- 3.1. idx_submissions_org_form on submissions(organization_id, form_id)
SET @equiv_idx_name := (
    SELECT INDEX_NAME
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'formflow_db'
      AND TABLE_NAME = 'submissions'
    GROUP BY INDEX_NAME
    HAVING GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX ASC SEPARATOR ',') = 'organization_id,form_id'
        OR GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX ASC SEPARATOR ',') LIKE 'organization_id,form_id,%'
    ORDER BY (INDEX_NAME = 'idx_submissions_org_form') DESC, INDEX_NAME ASC
    LIMIT 1
);
SET @name_conflict_cols := (
    SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX ASC SEPARATOR ',')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'formflow_db'
      AND TABLE_NAME = 'submissions'
      AND INDEX_NAME = 'idx_submissions_org_form'
);
SET @v2_taken := (
    SELECT COUNT(DISTINCT INDEX_NAME)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'formflow_db'
      AND TABLE_NAME = 'submissions'
      AND INDEX_NAME = 'idx_submissions_org_form_v2'
);

SELECT CASE
    WHEN @equiv_idx_name IS NOT NULL THEN
        CONCAT('OK/SKIPPED: Ordered columns (organization_id, form_id) already indexed by ', @equiv_idx_name)
    WHEN @name_conflict_cols IS NOT NULL THEN
        CONCAT('WARNING: Index name idx_submissions_org_form exists with different columns (', @name_conflict_cols, '). Creating idx_submissions_org_form_v2 instead.')
    ELSE
        'CREATING: idx_submissions_org_form on submissions(organization_id, form_id)'
END AS idx_submissions_org_form_status;

SET @sql := CASE
    WHEN @equiv_idx_name IS NOT NULL THEN
        'SELECT 1'
    WHEN @name_conflict_cols IS NULL THEN
        'CREATE INDEX `idx_submissions_org_form` ON `submissions` (`organization_id`, `form_id`)'
    WHEN @v2_taken = 0 THEN
        'CREATE INDEX `idx_submissions_org_form_v2` ON `submissions` (`organization_id`, `form_id`)'
    ELSE
        'SELECT * FROM `ERROR_ABORT_idx_submissions_org_form_and_v2_name_conflict`'
END;
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3.2. idx_submissions_org_assigned on submissions(organization_id, assigned_to)
SET @equiv_idx_name := (
    SELECT INDEX_NAME
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'formflow_db'
      AND TABLE_NAME = 'submissions'
    GROUP BY INDEX_NAME
    HAVING GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX ASC SEPARATOR ',') = 'organization_id,assigned_to'
        OR GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX ASC SEPARATOR ',') LIKE 'organization_id,assigned_to,%'
    ORDER BY (INDEX_NAME = 'idx_submissions_org_assigned') DESC, INDEX_NAME ASC
    LIMIT 1
);
SET @name_conflict_cols := (
    SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX ASC SEPARATOR ',')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'formflow_db'
      AND TABLE_NAME = 'submissions'
      AND INDEX_NAME = 'idx_submissions_org_assigned'
);
SET @v2_taken := (
    SELECT COUNT(DISTINCT INDEX_NAME)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'formflow_db'
      AND TABLE_NAME = 'submissions'
      AND INDEX_NAME = 'idx_submissions_org_assigned_v2'
);

SELECT CASE
    WHEN @equiv_idx_name IS NOT NULL THEN
        CONCAT('OK/SKIPPED: Ordered columns (organization_id, assigned_to) already indexed by ', @equiv_idx_name)
    WHEN @name_conflict_cols IS NOT NULL THEN
        CONCAT('WARNING: Index name idx_submissions_org_assigned exists with different columns (', @name_conflict_cols, '). Creating idx_submissions_org_assigned_v2 instead.')
    ELSE
        'CREATING: idx_submissions_org_assigned on submissions(organization_id, assigned_to)'
END AS idx_submissions_org_assigned_status;

SET @sql := CASE
    WHEN @equiv_idx_name IS NOT NULL THEN
        'SELECT 1'
    WHEN @name_conflict_cols IS NULL THEN
        'CREATE INDEX `idx_submissions_org_assigned` ON `submissions` (`organization_id`, `assigned_to`)'
    WHEN @v2_taken = 0 THEN
        'CREATE INDEX `idx_submissions_org_assigned_v2` ON `submissions` (`organization_id`, `assigned_to`)'
    ELSE
        'SELECT * FROM `ERROR_ABORT_idx_submissions_org_assigned_and_v2_name_conflict`'
END;
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3.3. idx_users_org_role on users(organization_id, role)
SET @equiv_idx_name := (
    SELECT INDEX_NAME
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'formflow_db'
      AND TABLE_NAME = 'users'
    GROUP BY INDEX_NAME
    HAVING GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX ASC SEPARATOR ',') = 'organization_id,role'
        OR GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX ASC SEPARATOR ',') LIKE 'organization_id,role,%'
    ORDER BY (INDEX_NAME = 'idx_users_org_role') DESC, INDEX_NAME ASC
    LIMIT 1
);
SET @name_conflict_cols := (
    SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX ASC SEPARATOR ',')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'formflow_db'
      AND TABLE_NAME = 'users'
      AND INDEX_NAME = 'idx_users_org_role'
);
SET @v2_taken := (
    SELECT COUNT(DISTINCT INDEX_NAME)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'formflow_db'
      AND TABLE_NAME = 'users'
      AND INDEX_NAME = 'idx_users_org_role_v2'
);

SELECT CASE
    WHEN @equiv_idx_name IS NOT NULL THEN
        CONCAT('OK/SKIPPED: Ordered columns (organization_id, role) already indexed by ', @equiv_idx_name)
    WHEN @name_conflict_cols IS NOT NULL THEN
        CONCAT('WARNING: Index name idx_users_org_role exists with different columns (', @name_conflict_cols, '). Creating idx_users_org_role_v2 instead.')
    ELSE
        'CREATING: idx_users_org_role on users(organization_id, role)'
END AS idx_users_org_role_status;

SET @sql := CASE
    WHEN @equiv_idx_name IS NOT NULL THEN
        'SELECT 1'
    WHEN @name_conflict_cols IS NULL THEN
        'CREATE INDEX `idx_users_org_role` ON `users` (`organization_id`, `role`)'
    WHEN @v2_taken = 0 THEN
        'CREATE INDEX `idx_users_org_role_v2` ON `users` (`organization_id`, `role`)'
    ELSE
        'SELECT * FROM `ERROR_ABORT_idx_users_org_role_and_v2_name_conflict`'
END;
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ==============================================================================
-- POST-MIGRATION VERIFICATION & DUPLICATE AUDIT QUERIES (Run in DBeaver)
-- ==============================================================================

-- 1. Confirm submissions.assigned_reviewer_id status and intact assigned_to data
SELECT COUNT(*) AS remaining_legacy_column
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'formflow_db'
  AND TABLE_NAME = 'submissions'
  AND COLUMN_NAME = 'assigned_reviewer_id';

SELECT COUNT(*) AS total_submissions, COUNT(assigned_to) AS assigned_submissions
FROM `submissions`;

-- 2. Verify all foreign keys and DELETE rules across users, forms, submissions
SELECT
    tc.TABLE_NAME,
    tc.CONSTRAINT_NAME,
    kcu.COLUMN_NAME,
    kcu.REFERENCED_TABLE_NAME,
    kcu.REFERENCED_COLUMN_NAME,
    rc.DELETE_RULE
FROM information_schema.TABLE_CONSTRAINTS tc
JOIN information_schema.KEY_COLUMN_USAGE kcu
  ON tc.CONSTRAINT_SCHEMA = kcu.CONSTRAINT_SCHEMA
 AND tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME
 AND tc.TABLE_NAME = kcu.TABLE_NAME
JOIN information_schema.REFERENTIAL_CONSTRAINTS rc
  ON tc.CONSTRAINT_SCHEMA = rc.CONSTRAINT_SCHEMA
 AND tc.CONSTRAINT_NAME = rc.CONSTRAINT_NAME
WHERE tc.TABLE_SCHEMA = 'formflow_db'
  AND tc.CONSTRAINT_TYPE = 'FOREIGN KEY'
  AND tc.TABLE_NAME IN ('users', 'forms', 'submissions')
ORDER BY tc.TABLE_NAME, kcu.COLUMN_NAME, tc.CONSTRAINT_NAME;

-- 3. Verify required ordered composite indexes exist (whether primary name or _v2)
SELECT
    TABLE_NAME,
    INDEX_NAME,
    GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX ASC SEPARATOR ',') AS ordered_columns,
    MAX(NON_UNIQUE) AS is_non_unique
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'formflow_db'
  AND TABLE_NAME IN ('users', 'submissions')
GROUP BY TABLE_NAME, INDEX_NAME
HAVING ordered_columns = 'organization_id,form_id'
    OR ordered_columns LIKE 'organization_id,form_id,%'
    OR ordered_columns = 'organization_id,assigned_to'
    OR ordered_columns LIKE 'organization_id,assigned_to,%'
    OR ordered_columns = 'organization_id,role'
    OR ordered_columns LIKE 'organization_id,role,%'
ORDER BY TABLE_NAME, INDEX_NAME;

-- 4. Detect any duplicate indexes on identical ordered column lists, plus users.email check
SELECT TABLE_NAME, ordered_columns, COUNT(*) AS duplicate_index_count, GROUP_CONCAT(INDEX_NAME) AS index_names
FROM (
    SELECT TABLE_NAME, INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX ASC SEPARATOR ',') AS ordered_columns
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'formflow_db'
      AND TABLE_NAME IN ('users', 'forms', 'submissions')
    GROUP BY TABLE_NAME, INDEX_NAME
) idx_summary
GROUP BY TABLE_NAME, ordered_columns
HAVING COUNT(*) > 1;

SELECT TABLE_NAME, COLUMN_NAME, COUNT(*) AS unique_index_count, GROUP_CONCAT(INDEX_NAME) AS index_names
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'formflow_db'
  AND TABLE_NAME = 'users'
  AND COLUMN_NAME = 'email'
  AND NON_UNIQUE = 0
GROUP BY TABLE_NAME, COLUMN_NAME;
