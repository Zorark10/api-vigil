-- V1__user_ownership.sql
--
-- Adds per-account workspace ownership without touching any existing data:
--   * app_user gains an (unique) email column; existing rows get a
--     deterministic placeholder address.
--   * owner_id columns + foreign keys are added to monitor,
--     notification_recipient and notification_settings. Every existing
--     monitor, recipient and settings row is assigned to the provisioned
--     administrator account so the original workspace keeps working.
--   * The global unique(email) constraint on notification_recipient is
--     replaced by a per-owner unique(owner_id, email): two accounts may use
--     the same alert address.
--   * notification_settings becomes one row per owner instead of one global
--     row.
--   * Supporting indexes: monitor(owner_id), monitor_result(monitor_id),
--     incident(monitor_id), notification_recipient(owner_id).
--
-- Runs only when the legacy tables already exist. On a brand-new empty
-- database it is a no-op: Hibernate's ddl-auto=update creates the full schema
-- (including the ownership columns) right after this migration.

DO $$
DECLARE
    constraint_name text;
BEGIN
    IF to_regclass('public.monitor') IS NULL OR to_regclass('public.app_user') IS NULL THEN
        RETURN;
    END IF;

    -- app_user.email (unique; legacy admin gets a placeholder address)
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = 'public' AND table_name = 'app_user' AND column_name = 'email') THEN
        ALTER TABLE app_user ADD COLUMN email varchar(255);
        UPDATE app_user SET email = lower(username) || '@localhost.local' WHERE email IS NULL;
        ALTER TABLE app_user ALTER COLUMN email SET NOT NULL;
        ALTER TABLE app_user ADD CONSTRAINT uk_app_user_email UNIQUE (email);
    END IF;

    -- monitor.owner_id: every existing monitor belongs to the administrator
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = 'public' AND table_name = 'monitor' AND column_name = 'owner_id') THEN
        ALTER TABLE monitor ADD COLUMN owner_id integer;
        UPDATE monitor SET owner_id = (
            SELECT u.id FROM app_user u
            ORDER BY CASE WHEN u.role = 'ADMIN' THEN 0 ELSE 1 END, u.id
            LIMIT 1)
        WHERE owner_id IS NULL;
        ALTER TABLE monitor ALTER COLUMN owner_id SET NOT NULL;
        ALTER TABLE monitor ADD CONSTRAINT fk_monitor_owner FOREIGN KEY (owner_id) REFERENCES app_user (id);
    END IF;
    CREATE INDEX IF NOT EXISTS idx_monitor_owner ON monitor (owner_id);

    IF to_regclass('public.monitor_result') IS NOT NULL THEN
        CREATE INDEX IF NOT EXISTS idx_monitor_result_monitor_id ON monitor_result (monitor_id);
    END IF;

    IF to_regclass('public.incident') IS NOT NULL THEN
        CREATE INDEX IF NOT EXISTS idx_incident_monitor_id ON incident (monitor_id);
    END IF;

    -- notification_recipient becomes owner-scoped (address unique per owner)
    IF to_regclass('public.notification_recipient') IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_schema = 'public' AND table_name = 'notification_recipient'
                       AND column_name = 'owner_id') THEN
        ALTER TABLE notification_recipient ADD COLUMN owner_id integer;
        UPDATE notification_recipient SET owner_id = (
            SELECT u.id FROM app_user u
            ORDER BY CASE WHEN u.role = 'ADMIN' THEN 0 ELSE 1 END, u.id
            LIMIT 1)
        WHERE owner_id IS NULL;
        ALTER TABLE notification_recipient ALTER COLUMN owner_id SET NOT NULL;
        ALTER TABLE notification_recipient ADD CONSTRAINT fk_recipient_owner
            FOREIGN KEY (owner_id) REFERENCES app_user (id);

        FOR constraint_name IN
            SELECT c.conname FROM pg_constraint c
            WHERE c.conrelid = 'notification_recipient'::regclass
              AND c.contype = 'u'
              AND EXISTS (SELECT 1 FROM pg_attribute a
                          WHERE a.attrelid = c.conrelid AND a.attname = 'email'
                            AND a.attnum = ANY (c.conkey))
        LOOP
            EXECUTE format('ALTER TABLE notification_recipient DROP CONSTRAINT %I', constraint_name);
        END LOOP;

        ALTER TABLE notification_recipient ADD CONSTRAINT uk_recipient_owner_email UNIQUE (owner_id, email);
    END IF;
    IF to_regclass('public.notification_recipient') IS NOT NULL THEN
        CREATE INDEX IF NOT EXISTS idx_recipient_owner ON notification_recipient (owner_id);
    END IF;

    -- notification_settings: one row per owner instead of one global row
    IF to_regclass('public.notification_settings') IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_schema = 'public' AND table_name = 'notification_settings'
                       AND column_name = 'owner_id') THEN
        ALTER TABLE notification_settings ADD COLUMN owner_id integer;
        UPDATE notification_settings SET owner_id = (
            SELECT u.id FROM app_user u
            ORDER BY CASE WHEN u.role = 'ADMIN' THEN 0 ELSE 1 END, u.id
            LIMIT 1)
        WHERE owner_id IS NULL;
        ALTER TABLE notification_settings ALTER COLUMN owner_id SET NOT NULL;
        ALTER TABLE notification_settings ADD CONSTRAINT fk_settings_owner
            FOREIGN KEY (owner_id) REFERENCES app_user (id);
        ALTER TABLE notification_settings ADD CONSTRAINT uk_settings_owner UNIQUE (owner_id);
    END IF;
END $$;
