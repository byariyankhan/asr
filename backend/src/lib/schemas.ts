import { z } from "zod";
import { isValidTimeZone } from "./time";
import { UUID_RE } from "./uuid";

// Request bodies, validated at the edge of every route. Shapes match
// docs/API.md; anything not listed here is rejected with 400 invalid_body.

export const uuid = z.string().regex(UUID_RE, "must be a UUID");

const timeZone = z.string().min(1).max(64).refine(isValidTimeZone, "unknown IANA timezone");

// Android package names: dotted identifiers, e.g. com.instagram.android
const packageName = z
  .string()
  .min(3)
  .max(255)
  .regex(/^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$/, "not an Android package name");

export const deviceRegister = z.object({
  install_id: z.string().min(8).max(128),
  model: z.string().max(128).optional(),
  os_version: z.string().max(32).optional(),
  app_version: z.string().min(1).max(32),
  fcm_token: z.string().min(1).max(4096).optional(),
});
export type DeviceRegister = z.infer<typeof deviceRegister>;

export const heartbeat = z.object({
  protection_enabled: z.boolean(),
  app_version: z.string().min(1).max(32),
  fcm_token: z.string().min(1).max(4096).optional(),
});
export type Heartbeat = z.infer<typeof heartbeat>;

// Presets (7/14/21/30 in the current design) are a UI concern; the server
// accepts any whole number of days in this range, which also covers the
// "custom duration" option.
export const MIN_DURATION_DAYS = 1;
export const MAX_DURATION_DAYS = 90;

const challengeRule = z.object({
  reward_min: z.number().int().min(1).max(120),
  daily_cap_min: z.number().int().min(1).max(240),
});

export const snapshot = z.object({
  apps: z
    .array(
      z.object({
        package: packageName,
        label: z.string().min(1).max(128),
        // 0 = fully blocked
        daily_limit_min: z.number().int().min(0).max(1440),
      }),
    )
    .min(1)
    .max(100)
    .refine(
      (apps) => new Set(apps.map((a) => a.package)).size === apps.length,
      "duplicate package",
    ),
  reset_time: z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/, "HH:MM"),
  challenges: z
    .object({
      walk_steps: challengeRule.extend({ target: z.number().int().min(100).max(50_000) }).optional(),
      focus_session: challengeRule.extend({ target_min: z.number().int().min(5).max(180) }).optional(),
      waiting_period: challengeRule.extend({ wait_min: z.number().int().min(1).max(60) }).optional(),
    })
    .default({}),
});
export type Snapshot = z.infer<typeof snapshot>;

export const commitmentCreate = z.object({
  device_id: uuid,
  duration_days: z.number().int().min(MIN_DURATION_DAYS).max(MAX_DURATION_DAYS),
  timezone: timeZone,
  snapshot,
});
export type CommitmentCreate = z.infer<typeof commitmentCreate>;

// Event types a device may report. Server-only types (protection_lost,
// uninstalled, challenge_failed, started) are refused here on purpose.
export const DEVICE_EVENT_TYPES = ["broken", "completed", "limit_hit", "challenge_completed", "restored"] as const;
export const EVENT_REASONS = [
  "limit_exceeded",
  "app_removed",
  "protection_disabled",
  "permission_revoked",
  "user_gave_up",
] as const;

export const eventCreate = z
  .object({
    id: uuid,
    type: z.enum(DEVICE_EVENT_TYPES),
    reason: z.enum(EVENT_REASONS).optional(),
    app_package: packageName.optional(),
    minutes: z.number().int().min(1).max(240).optional(),
    occurred_at: z.iso.datetime({ offset: true }),
  })
  .refine((e) => e.type !== "broken" || e.reason !== undefined, {
    message: "a broken event needs a reason",
    path: ["reason"],
  })
  .refine((e) => e.type !== "challenge_completed" || e.minutes !== undefined, {
    message: "challenge_completed needs minutes",
    path: ["minutes"],
  });
export type EventCreate = z.infer<typeof eventCreate>;

export const giveUp = z.object({ id: uuid });

export const listQuery = z.object({
  cursor: z.string().max(80).optional(),
  limit: z.coerce.number().int().min(1).max(100).default(20),
});
