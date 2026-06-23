-- Seed vehicles for DistriSur Alimentación
INSERT INTO vehicles (company_id, depot_id, plate, capacity) VALUES
('cb4eeabf-faa8-47ef-a012-d386b9a8821a', '74e147d9-1994-4e94-945a-ea54366ccd2b', 'DSA-001', 500),
('cb4eeabf-faa8-47ef-a012-d386b9a8821a', '74e147d9-1994-4e94-945a-ea54366ccd2b', 'DSA-002', 300),
('cb4eeabf-faa8-47ef-a012-d386b9a8821a', 'f2a14f4b-53cf-4f93-bfe4-8690fa14948f', 'DSA-003', 400),
('cb4eeabf-faa8-47ef-a012-d386b9a8821a', 'c5f10626-cc9e-4d70-9882-811d6e98e329', 'DSA-004', 150)
ON CONFLICT (company_id, plate) DO NOTHING;

-- Seed vehicles for DistriSur Industrial
INSERT INTO vehicles (company_id, depot_id, plate, capacity) VALUES
('22fbe221-9de5-41c7-841c-4d9ed1a226f8', '9fe53a29-fdcf-4540-abcf-9053beafde1f', 'DSI-001', 1000),
('22fbe221-9de5-41c7-841c-4d9ed1a226f8', 'bb73c276-7e87-4e48-9307-116583ba1695', 'DSI-002', 600)
ON CONFLICT (company_id, plate) DO NOTHING;
