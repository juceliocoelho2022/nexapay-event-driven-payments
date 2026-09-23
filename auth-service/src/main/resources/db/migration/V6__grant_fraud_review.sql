INSERT INTO permissions (id, name)
VALUES ('00000000-0000-0000-0000-000000000109', 'FRAUD_REVIEW')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name = 'FRAUD_REVIEW'
WHERE r.name = 'ROLE_ADMIN'
ON CONFLICT DO NOTHING;
