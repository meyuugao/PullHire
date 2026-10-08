# Event contracts

JSON Schema (draft 2020-12) for the events exchanged on the `pullhire.events`
topic exchange (`docs/architecture.md §4.2`). Every message is wrapped in the
common [envelope](envelope.schema.json); per-event files describe the envelope's
`payload`. CI checks backwards compatibility (`docs/architecture.md §4.4`, §7.3).

## Version policy

- The envelope's `version` starts at **1** (`envelope.schema.json`, `const: 1`).
- Increment `version` **only** on an incompatible payload change (field removed,
  type narrowed, required semantics changed). Additive, optional fields do not
  bump the version.
- Producers and consumers must tolerate unknown fields (consumers ignore them);
  removing/renaming a field is a breaking change and requires a new version.
- Name changes to the `type`/routing key are breaking and are registered in
  `docs/backlog/closed_questions.md`.

## Registry

Canonical names come from `docs/architecture.md §1.2` and `§3.1`. Status is
`active` once a producer/consumer ships, otherwise `planned`.

| Event | Producer | Primary consumers | Payload schema | Status |
|---|---|---|---|---|
| `user.registered` | auth-service | notification-service | [user.registered](user.registered.schema.json) | active |
| `user.email_verified` | auth-service | — | planned | planned |
| `consent.updated` | auth-service | — | planned | planned |
| `candidate.card_updated` | candidate-service | selection-service | [candidate.card_updated](candidate.card_updated.schema.json) | active |
| `candidate.contact_released` | candidate-service | outreach-service, notification-service | [candidate.contact_released](candidate.contact_released.schema.json) | active |
| `fsp.linked` | fsp-service | candidate-service (audit) | [fsp.linked](fsp.linked.schema.json) | active |
| `fsp.achievements_updated` | fsp-service | selection-service | [fsp.achievements_updated](fsp.achievements_updated.schema.json) | active |
| `assessment.category_assigned` | assessment-service | selection-service | planned | planned |
| `assessment.grade_changed` | assessment-service | notification-service (audit) | planned | planned |
| `invitation.sent` | outreach-service | notification-service | planned | planned |
| `invitation.viewed` | outreach-service | — | planned | planned |
| `invitation.accepted` | outreach-service | candidate-service | [invitation.accepted](invitation.accepted.schema.json) | active |
| `invitation.declined` | outreach-service | — | planned | planned |

`assessment.task_completed` (`[ext]`) and any `[opt]` events are out of the MVP
core scope (`docs/architecture.md §3.1` footnotes) and are not registered yet.
