-- Organización y empresa de sistema para el GLOBAL_ADMIN.
-- El admin no debe estar vinculado a ninguna organización de cliente.

INSERT INTO organizations (id, name, handle, created_at)
SELECT gen_random_uuid(), 'Delivera', 'delivera', NOW()
WHERE NOT EXISTS (SELECT 1 FROM organizations WHERE handle = 'delivera');

INSERT INTO companies (id, name, organization_id, activity_type, plan_code, created_at)
SELECT gen_random_uuid(), 'Delivera Platform', o.id, 'DISTRIBUTION', 'FREE', NOW()
FROM organizations o
WHERE o.handle = 'delivera'
  AND NOT EXISTS (
      SELECT 1 FROM companies c
      JOIN organizations org ON c.organization_id = org.id
      WHERE org.handle = 'delivera'
  );

-- Mover todos los workers con rol GLOBAL_ADMIN a la empresa de sistema
UPDATE workers
SET company_id = (
    SELECT c.id FROM companies c
    JOIN organizations o ON c.organization_id = o.id
    WHERE o.handle = 'delivera'
    LIMIT 1
)
WHERE role = 'GLOBAL_ADMIN';
