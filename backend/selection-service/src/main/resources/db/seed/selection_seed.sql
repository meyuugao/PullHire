-- ST-7.2 selection.taxonomy seed (docs/assumptions.md §4; qa-summary §2).
-- MVP catalogue only: industry `it`, 8 specializations
-- (backend, frontend, fullstack, mobile, devops, qa, data, ml) and 5 grades
-- (intern/junior/middle/senior/lead, grade_order 0..4). Category = specialization x grade.
-- Idempotent on the natural key (kind, code) (uq_taxonomy_kind_code from
-- V1__create_taxonomy.sql): re-running skips rows that are already present.
-- parent_id resolved via subselects, no hard-coded ids. File-only seed:
-- executed via the S12 one-shot container, not wired anywhere.

-- industries
INSERT INTO taxonomy (kind, code, name, parent_id, grade_order, is_active)
VALUES ('industry', 'it', 'IT', NULL, NULL, true)
ON CONFLICT (kind, code) DO NOTHING;

-- specializations (children of industry `it`)
INSERT INTO taxonomy (kind, code, name, parent_id, grade_order, is_active)
VALUES
    ('specialization', 'backend', 'Backend', (SELECT id FROM taxonomy WHERE kind = 'industry' AND code = 'it'), NULL, true),
    ('specialization', 'frontend', 'Frontend', (SELECT id FROM taxonomy WHERE kind = 'industry' AND code = 'it'), NULL, true),
    ('specialization', 'fullstack', 'Fullstack', (SELECT id FROM taxonomy WHERE kind = 'industry' AND code = 'it'), NULL, true),
    ('specialization', 'mobile', 'Mobile', (SELECT id FROM taxonomy WHERE kind = 'industry' AND code = 'it'), NULL, true),
    ('specialization', 'devops', 'DevOps', (SELECT id FROM taxonomy WHERE kind = 'industry' AND code = 'it'), NULL, true),
    ('specialization', 'qa', 'QA', (SELECT id FROM taxonomy WHERE kind = 'industry' AND code = 'it'), NULL, true),
    ('specialization', 'data', 'Data / Analytics', (SELECT id FROM taxonomy WHERE kind = 'industry' AND code = 'it'), NULL, true),
    ('specialization', 'ml', 'ML', (SELECT id FROM taxonomy WHERE kind = 'industry' AND code = 'it'), NULL, true)
ON CONFLICT (kind, code) DO NOTHING;

-- grades
INSERT INTO taxonomy (kind, code, name, parent_id, grade_order, is_active)
VALUES
    ('grade', 'intern', 'Intern', NULL, 0, true),
    ('grade', 'junior', 'Junior', NULL, 1, true),
    ('grade', 'middle', 'Middle', NULL, 2, true),
    ('grade', 'senior', 'Senior', NULL, 3, true),
    ('grade', 'lead', 'Lead', NULL, 4, true)
ON CONFLICT (kind, code) DO NOTHING;
