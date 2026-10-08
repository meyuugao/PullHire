-- S4 fsp seed (docs/fsp_integration.md §4). Idempotent synthetic sets for the demo:
--   * present set: linked links WITH 2-5 achievements each, spanning winner (place 1),
--     placer (place >= 2) and participant (place NULL) outcomes;
--   * no_history set: linked links with ZERO achievements (docs/fsp_integration.md §6).
-- candidate_id is a logical reference to candidate.profiles.id (no cross-schema FK);
-- the referenced ids are created by the S12 demo seed. No events are published from
-- seed (S12 concern): seed writes business rows only.

INSERT INTO fsp_links (candidate_id, fsp_id, link_status, history_status, linked_at, last_sync_at)
VALUES
    (1, 'fsp-demo-001', 'linked', 'present',    now(), now()),
    (2, 'fsp-demo-002', 'linked', 'present',    now(), now()),
    (3, 'fsp-demo-003', 'linked', 'no_history', now(), now()),
    (4, 'fsp-demo-004', 'linked', 'no_history', now(), now())
ON CONFLICT DO NOTHING;

-- present set: candidate 1 = winner + placer + participant; candidate 2 = placer + participant.
INSERT INTO achievements_cache (fsp_link_id, external_achievement_id, title, event_type, achieved_at, place, payload)
VALUES
    ((SELECT id FROM fsp_links WHERE candidate_id = 1), 'fsp-demo-001-win',  'Победа в финале ФСП',        'championship', DATE '2023-05-14', 1,    NULL),
    ((SELECT id FROM fsp_links WHERE candidate_id = 1), 'fsp-demo-001-place', 'Призёр регионального этапа', 'championship', DATE '2022-10-02', 2,    NULL),
    ((SELECT id FROM fsp_links WHERE candidate_id = 1), 'fsp-demo-001-part', 'Участие в отборочном туре',  'selection',    DATE '2021-09-19', NULL, NULL),
    ((SELECT id FROM fsp_links WHERE candidate_id = 2), 'fsp-demo-002-place', 'Бронза всероссийского этапа', 'championship', DATE '2023-03-11', 3,    NULL),
    ((SELECT id FROM fsp_links WHERE candidate_id = 2), 'fsp-demo-002-part', 'Участие в отборочном туре',  'selection',    DATE '2020-11-07', NULL, NULL)
ON CONFLICT (fsp_link_id, external_achievement_id) DO NOTHING;

-- no_history set: candidates 3 and 4 intentionally have no achievements_cache rows.
