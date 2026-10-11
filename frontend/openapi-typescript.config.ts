// Generator config: per-service TypeScript schemas built from the committed
// OpenAPI baselines (docs/examples/openapi/<service>.json, see
// docs/examples/README.md §2). Regenerate with `npm run generate:clients`
// (the script mirrors this mapping). CI job `client-regen` fails when the
// committed output is stale (docs/architecture.md §4.4, §7.3).
export const OPENAPI_SERVICES = [
  "auth",
  "candidate",
  "fsp",
  "assessment",
  "outreach",
  "notification",
] as const;

export type OpenApiService = (typeof OPENAPI_SERVICES)[number];

export interface ServiceClientTarget {
  /** Baseline spec, relative to frontend/. */
  schema: string;
  /** Generated types output, relative to frontend/. */
  output: string;
}

export const SERVICE_CLIENT_TARGETS: Record<OpenApiService, ServiceClientTarget> = {
  auth: {
    schema: "../docs/examples/openapi/auth.json",
    output: "./lib/api/generated/auth/schema.d.ts",
  },
  candidate: {
    schema: "../docs/examples/openapi/candidate.json",
    output: "./lib/api/generated/candidate/schema.d.ts",
  },
  fsp: {
    schema: "../docs/examples/openapi/fsp.json",
    output: "./lib/api/generated/fsp/schema.d.ts",
  },
  assessment: {
    schema: "../docs/examples/openapi/assessment.json",
    output: "./lib/api/generated/assessment/schema.d.ts",
  },
  outreach: {
    schema: "../docs/examples/openapi/outreach.json",
    output: "./lib/api/generated/outreach/schema.d.ts",
  },
  notification: {
    schema: "../docs/examples/openapi/notification.json",
    output: "./lib/api/generated/notification/schema.d.ts",
  },
};
