-- S5.13 assessment item_bank seed (docs/testing_model.md §3.1–§3.4, §3.6;
-- docs/assumptions.md §6, §9 Decision D). Synthetic calibrated bank: one anchor
-- plus template families per MVP specialization (docs/assumptions.md §4).
-- payload carries only the item model (radical) and its key; variant
-- incidentals are resolved downstream from test_sessions.form_seed /
-- attempts.variant_seed. Only auto-checkable formats are seeded
-- (single/multiple choice, short answer, code); open-ended prose answers are
-- excluded by Decision D. Business rows only: this script writes assessment.item_bank and
-- no event rows. The statement is idempotent on the natural key
-- (item_kind, specialization_code, concept_code, template_family): re-running
-- skips rows that are already present. Template items carry exposure_limit 100
-- (app.assessment.exposure-default default; TD-5 retire path); anchors carry
-- NULL (no cap).

INSERT INTO assessment.item_bank
    (item_kind, template_family, concept_code, specialization_code, difficulty, is_provisional, exposure_limit, payload, status)
SELECT v.item_kind, v.template_family, v.concept_code, v.specialization_code,
       v.difficulty::numeric(5,3), v.is_provisional, v.exposure_limit, v.payload::jsonb, v.status
FROM (VALUES
    -- backend
    ('anchor',   NULL,                                       'backend.anchor.baseline',          'backend',  0.000, true, NULL, '{"model":{"format":"short_answer","statement":"Which HTTP status code signals an internal server error?","answer_type":"integer"},"key":{"value":500}}', 'active'),
    ('template', 'backend.family.single-choice',             'backend.concept.single-choice',    'backend', -1.000, true, 100, '{"model":{"format":"single_choice","statement":"Choose the single correct option for {{scenario}}.","parameters":["scenario"],"options":["option-a","option-b","option-c","option-d"]},"key":{"correct":"option-b"}}', 'active'),
    ('template', 'backend.family.multiple-choice',           'backend.concept.multiple-choice',  'backend',  0.000, true, 100, '{"model":{"format":"multiple_choice","statement":"Select every statement that holds for {{scenario}}.","parameters":["scenario"],"options":["statement-a","statement-b","statement-c","statement-d"]},"key":{"correct":["statement-a","statement-c"]}}', 'active'),
    ('template', 'backend.family.code',                      'backend.concept.code',             'backend',  1.000, true, 100, '{"model":{"format":"code","statement":"Implement {{function}} to satisfy the specification.","parameters":["function"],"language":"java"},"key":{"reference_tests":"tests specify the expected behaviour"}}', 'active'),
    -- frontend
    ('anchor',   NULL,                                       'frontend.anchor.baseline',         'frontend',  0.000, true, NULL, '{"model":{"format":"short_answer","statement":"Which CSS property controls the stacking order of positioned elements?","answer_type":"string"},"key":{"value":"z-index"}}', 'active'),
    ('template', 'frontend.family.single-choice',            'frontend.concept.single-choice',   'frontend', -1.000, true, 100, '{"model":{"format":"single_choice","statement":"Choose the single correct option for {{scenario}}.","parameters":["scenario"],"options":["option-a","option-b","option-c","option-d"]},"key":{"correct":"option-b"}}', 'active'),
    ('template', 'frontend.family.multiple-choice',          'frontend.concept.multiple-choice', 'frontend',  0.000, true, 100, '{"model":{"format":"multiple_choice","statement":"Select every statement that holds for {{scenario}}.","parameters":["scenario"],"options":["statement-a","statement-b","statement-c","statement-d"]},"key":{"correct":["statement-a","statement-c"]}}', 'active'),
    ('template', 'frontend.family.code',                     'frontend.concept.code',            'frontend',  1.000, true, 100, '{"model":{"format":"code","statement":"Implement {{function}} to satisfy the specification.","parameters":["function"],"language":"java"},"key":{"reference_tests":"tests specify the expected behaviour"}}', 'active'),
    -- fullstack
    ('anchor',   NULL,                                       'fullstack.anchor.baseline',        'fullstack',  0.000, true, NULL, '{"model":{"format":"short_answer","statement":"Which HTTP method is idempotent and replaces the target resource?","answer_type":"string"},"key":{"value":"PUT"}}', 'active'),
    ('template', 'fullstack.family.single-choice',           'fullstack.concept.single-choice',  'fullstack', -1.000, true, 100, '{"model":{"format":"single_choice","statement":"Choose the single correct option for {{scenario}}.","parameters":["scenario"],"options":["option-a","option-b","option-c","option-d"]},"key":{"correct":"option-b"}}', 'active'),
    ('template', 'fullstack.family.multiple-choice',         'fullstack.concept.multiple-choice','fullstack',  0.000, true, 100, '{"model":{"format":"multiple_choice","statement":"Select every statement that holds for {{scenario}}.","parameters":["scenario"],"options":["statement-a","statement-b","statement-c","statement-d"]},"key":{"correct":["statement-a","statement-c"]}}', 'active'),
    ('template', 'fullstack.family.code',                    'fullstack.concept.code',           'fullstack',  1.000, true, 100, '{"model":{"format":"code","statement":"Implement {{function}} to satisfy the specification.","parameters":["function"],"language":"java"},"key":{"reference_tests":"tests specify the expected behaviour"}}', 'active'),
    -- mobile
    ('anchor',   NULL,                                       'mobile.anchor.baseline',           'mobile',  0.000, true, NULL, '{"model":{"format":"short_answer","statement":"Which Android lifecycle callback runs when an activity becomes visible?","answer_type":"string"},"key":{"value":"onStart"}}', 'active'),
    ('template', 'mobile.family.single-choice',              'mobile.concept.single-choice',     'mobile', -1.000, true, 100, '{"model":{"format":"single_choice","statement":"Choose the single correct option for {{scenario}}.","parameters":["scenario"],"options":["option-a","option-b","option-c","option-d"]},"key":{"correct":"option-b"}}', 'active'),
    ('template', 'mobile.family.multiple-choice',            'mobile.concept.multiple-choice',   'mobile',  0.000, true, 100, '{"model":{"format":"multiple_choice","statement":"Select every statement that holds for {{scenario}}.","parameters":["scenario"],"options":["statement-a","statement-b","statement-c","statement-d"]},"key":{"correct":["statement-a","statement-c"]}}', 'active'),
    ('template', 'mobile.family.code',                       'mobile.concept.code',              'mobile',  1.000, true, 100, '{"model":{"format":"code","statement":"Implement {{function}} to satisfy the specification.","parameters":["function"],"language":"java"},"key":{"reference_tests":"tests specify the expected behaviour"}}', 'active'),
    -- devops
    ('anchor',   NULL,                                       'devops.anchor.baseline',           'devops',  0.000, true, NULL, '{"model":{"format":"short_answer","statement":"Which command builds a container image from a Dockerfile?","answer_type":"string"},"key":{"value":"docker build"}}', 'active'),
    ('template', 'devops.family.single-choice',              'devops.concept.single-choice',     'devops', -1.000, true, 100, '{"model":{"format":"single_choice","statement":"Choose the single correct option for {{scenario}}.","parameters":["scenario"],"options":["option-a","option-b","option-c","option-d"]},"key":{"correct":"option-b"}}', 'active'),
    ('template', 'devops.family.multiple-choice',            'devops.concept.multiple-choice',   'devops',  0.000, true, 100, '{"model":{"format":"multiple_choice","statement":"Select every statement that holds for {{scenario}}.","parameters":["scenario"],"options":["statement-a","statement-b","statement-c","statement-d"]},"key":{"correct":["statement-a","statement-c"]}}', 'active'),
    ('template', 'devops.family.code',                       'devops.concept.code',              'devops',  1.000, true, 100, '{"model":{"format":"code","statement":"Implement {{function}} to satisfy the specification.","parameters":["function"],"language":"java"},"key":{"reference_tests":"tests specify the expected behaviour"}}', 'active'),
    -- qa
    ('anchor',   NULL,                                       'qa.anchor.baseline',               'qa',  0.000, true, NULL, '{"model":{"format":"short_answer","statement":"Which artifact describes the conditions and cases to be tested?","answer_type":"string"},"key":{"value":"test plan"}}', 'active'),
    ('template', 'qa.family.single-choice',                  'qa.concept.single-choice',         'qa', -1.000, true, 100, '{"model":{"format":"single_choice","statement":"Choose the single correct option for {{scenario}}.","parameters":["scenario"],"options":["option-a","option-b","option-c","option-d"]},"key":{"correct":"option-b"}}', 'active'),
    ('template', 'qa.family.multiple-choice',                'qa.concept.multiple-choice',       'qa',  0.000, true, 100, '{"model":{"format":"multiple_choice","statement":"Select every statement that holds for {{scenario}}.","parameters":["scenario"],"options":["statement-a","statement-b","statement-c","statement-d"]},"key":{"correct":["statement-a","statement-c"]}}', 'active'),
    ('template', 'qa.family.code',                           'qa.concept.code',                  'qa',  1.000, true, 100, '{"model":{"format":"code","statement":"Implement {{function}} to satisfy the specification.","parameters":["function"],"language":"java"},"key":{"reference_tests":"tests specify the expected behaviour"}}', 'active'),
    -- data
    ('anchor',   NULL,                                       'data.anchor.baseline',             'data',  0.000, true, NULL, '{"model":{"format":"short_answer","statement":"Which SQL clause filters rows after grouping?","answer_type":"string"},"key":{"value":"HAVING"}}', 'active'),
    ('template', 'data.family.single-choice',                'data.concept.single-choice',       'data', -1.000, true, 100, '{"model":{"format":"single_choice","statement":"Choose the single correct option for {{scenario}}.","parameters":["scenario"],"options":["option-a","option-b","option-c","option-d"]},"key":{"correct":"option-b"}}', 'active'),
    ('template', 'data.family.multiple-choice',              'data.concept.multiple-choice',     'data',  0.000, true, 100, '{"model":{"format":"multiple_choice","statement":"Select every statement that holds for {{scenario}}.","parameters":["scenario"],"options":["statement-a","statement-b","statement-c","statement-d"]},"key":{"correct":["statement-a","statement-c"]}}', 'active'),
    ('template', 'data.family.code',                         'data.concept.code',                'data',  1.000, true, 100, '{"model":{"format":"code","statement":"Implement {{function}} to satisfy the specification.","parameters":["function"],"language":"java"},"key":{"reference_tests":"tests specify the expected behaviour"}}', 'active'),
    -- ml
    ('anchor',   NULL,                                       'ml.anchor.baseline',               'ml',  0.000, true, NULL, '{"model":{"format":"short_answer","statement":"Which metric is the harmonic mean of precision and recall?","answer_type":"string"},"key":{"value":"F1"}}', 'active'),
    ('template', 'ml.family.single-choice',                  'ml.concept.single-choice',         'ml', -1.000, true, 100, '{"model":{"format":"single_choice","statement":"Choose the single correct option for {{scenario}}.","parameters":["scenario"],"options":["option-a","option-b","option-c","option-d"]},"key":{"correct":"option-b"}}', 'active'),
    ('template', 'ml.family.multiple-choice',                'ml.concept.multiple-choice',       'ml',  0.000, true, 100, '{"model":{"format":"multiple_choice","statement":"Select every statement that holds for {{scenario}}.","parameters":["scenario"],"options":["statement-a","statement-b","statement-c","statement-d"]},"key":{"correct":["statement-a","statement-c"]}}', 'active'),
    ('template', 'ml.family.code',                           'ml.concept.code',                  'ml',  1.000, true, 100, '{"model":{"format":"code","statement":"Implement {{function}} to satisfy the specification.","parameters":["function"],"language":"java"},"key":{"reference_tests":"tests specify the expected behaviour"}}', 'active')
) AS v(item_kind, template_family, concept_code, specialization_code, difficulty, is_provisional, exposure_limit, payload, status)
WHERE NOT EXISTS (
    SELECT 1
    FROM assessment.item_bank ib
    WHERE ib.item_kind = v.item_kind
      AND ib.specialization_code = v.specialization_code
      AND ib.concept_code = v.concept_code
      AND ib.template_family IS NOT DISTINCT FROM v.template_family
);
