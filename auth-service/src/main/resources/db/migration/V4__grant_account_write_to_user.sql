INSERT INTO role_permissions (role_id, permission_id)
SELECT
    '00000000-0000-0000-0000-000000000001'::uuid,
    id
FROM permissions
WHERE name = 'ACCOUNT_WRITE'
ON CONFLICT DO NOTHING;
