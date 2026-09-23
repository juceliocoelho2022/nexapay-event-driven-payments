INSERT INTO permissions (id, name)
VALUES ('00000000-0000-0000-0000-000000000108', 'PAYMENT_CANCEL')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT role_id, permission_id
FROM (
    SELECT
        r.id AS role_id,
        p.id AS permission_id
    FROM roles r
    CROSS JOIN permissions p
    WHERE r.name IN ('USER', 'ADMIN')
      AND p.name = 'PAYMENT_CANCEL'
) granted
ON CONFLICT DO NOTHING;
