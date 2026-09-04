import { z } from "zod";
import { isValidCountry, isValidTimeZone } from "./time";
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

const activityRule = z.object({
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
  activities: z
    .object({
      walk_steps: activityRule.extend({ target: z.number().int().min(100).max(50_000) }).optional(),
      focus_session: activityRule.extend({ target_min: z.number().int().min(5).max(180) }).optional(),
      waiting_period: activityRule.extend({ wait_min: z.number().int().min(1).max(60) }).optional(),
    })
    .default({}),
});
export type Snapshot = z.infer<typeof snapshot>;

export const pactCreate = z.object({
  device_id: uuid,
  duration_days: z.number().int().min(MIN_DURATION_DAYS).max(MAX_DURATION_DAYS),
  timezone: timeZone,
  snapshot,
});
export type PactCreate = z.infer<typeof pactCreate>;

// Event types a device may report. Server-only types (protection_lost,
// uninstalled, activity_failed, started) are refused here on purpose.
export const DEVICE_EVENT_TYPES = ["broken", "completed", "limit_hit", "activity_completed", "restored"] as const;
export const EVENT_REASONS = [
  "limit_exceeded",
  "app_removed",
  "protection_disabled",
  "permission_revoked",
  "user_gave_up",
] as const;
export type EventReason = (typeof EVENT_REASONS)[number];

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
  .refine((e) => e.type !== "activity_completed" || e.minutes !== undefined, {
    message: "activity_completed needs minutes",
    path: ["minutes"],
  });
export type EventCreate = z.infer<typeof eventCreate>;

export const giveUp = z.object({ id: uuid });

export const listQuery = z.object({
  cursor: z.string().max(80).optional(),
  limit: z.coerce.number().int().min(1).max(100).default(20),
});

export const GENDERS = ["male", "female", "other", "prefer_not_to_say"] as const;

// YYYY-MM-DD, a real calendar date, age 13..120 today.
const dateOfBirth = z
  .string()
  .regex(/^\d{4}-\d{2}-\d{2}$/, "YYYY-MM-DD")
  .refine((s) => {
    const d = new Date(`${s}T00:00:00Z`);
    if (Number.isNaN(d.getTime()) || d.toISOString().slice(0, 10) !== s) return false;
    const now = new Date();
    const age = now.getUTCFullYear() - d.getUTCFullYear() - (now < new Date(Date.UTC(now.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate())) ? 1 : 0);
    return age >= 13 && age <= 120;
  }, "must be a real date; users must be 13 or older");

export const meUpdate = z
  .object({
    name: z.string().trim().min(1).max(80).optional(),
    timezone: timeZone.optional(),
    notify_email: z.boolean().optional(),
    notify_push: z.boolean().optional(),
    date_of_birth: dateOfBirth.nullable().optional(),
    country: z.string().regex(/^[A-Z]{2}$/, "ISO 3166-1 alpha-2").refine(isValidCountry, "unknown country").nullable().optional(),
    gender: z.enum(GENDERS).nullable().optional(),
  })
  .refine((o) => Object.keys(o).length > 0, "nothing to update");
export type MeUpdate = z.infer<typeof meUpdate>;

const isoDateTime = z.iso.datetime({ offset: true });
const nonEmpty = (o: object) => Object.keys(o).length > 0;

// --- witnesses ---
// A witness is one person, so each value names one person. The lumped
// three -- parent, sibling, spouse -- are still accepted because rows
// written before the split exist; the app no longer offers them.
export const RELATIONSHIPS = [
  "mother",
  "father",
  "brother",
  "sister",
  "husband",
  "wife",
  "partner",
  "friend",
  "mentor",
  "colleague",
  "other",
  "parent",
  "sibling",
  "spouse",
] as const;

export const witnessInvite = z.object({
  relationship: z.enum(RELATIONSHIPS),
  email: z.email().max(254).optional(),
});
export type WitnessInvite = z.infer<typeof witnessInvite>;

// The witness edits how they are notified; the user edits what the witness
// may see and how they are labelled. Ownership is checked in the server.
export const witnessPatch = z
  .object({
    notify_start: z.boolean(),
    notify_success: z.boolean(),
    notify_failure: z.boolean(),
    notify_digest: z.boolean(),
    roast_mode: z.boolean(),
    views_progress: z.boolean(),
    relationship: z.enum(RELATIONSHIPS),
  })
  .partial()
  .refine(nonEmpty, "nothing to update");
export type WitnessPatch = z.infer<typeof witnessPatch>;

export const EMOJIS = ["laugh", "haha", "shoe", "tomato", "clap"] as const;
export const reactionCreate = z.object({ event_id: uuid, emoji: z.enum(EMOJIS) });
export const reactionDelete = z.object({ event_id: uuid });

// --- activities (earn your time) ---
export const ACTIVITY_TYPES = ["walk_steps", "focus_session", "waiting_period"] as const;

export const activityCreate = z
  .object({
    id: uuid,
    type: z.enum(ACTIVITY_TYPES),
    started_at: isoDateTime,
    deadline_at: isoDateTime,
    // Which app's limit sent them here. Optional: an earn started from
    // anywhere else is still an earn, and the witness copy falls back to
    // "their limit" rather than naming the wrong app.
    app_package: packageName.optional(),
  })
  .refine((a) => new Date(a.deadline_at) > new Date(a.started_at), {
    message: "deadline must be after start",
    path: ["deadline_at"],
  });
export type ActivityCreate = z.infer<typeof activityCreate>;

export const activityComplete = z.object({ event_id: uuid, occurred_at: isoDateTime });
export type ActivityComplete = z.infer<typeof activityComplete>;

// --- daily summary ---
export const summaryCreate = z.object({
  day: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "YYYY-MM-DD"),
  apps: z
    .array(
      z.object({
        package: packageName,
        minutes_used: z.number().int().min(0).max(1440),
        limit_min: z.number().int().min(0).max(1440),
        earned_min: z.number().int().min(0).max(600).default(0),
      }),
    )
    .min(1)
    .max(100),
});
export type SummaryCreate = z.infer<typeof summaryCreate>;

export const notificationsRead = z.union([
  z.object({ ids: z.array(uuid).min(1).max(200) }),
  z.object({ all: z.literal(true) }),
]);

export const accountDelete = z.object({ password: z.string().min(1).max(256) });

export const subscriptionVerify = z.object({
  // product_id is not read: Play is the authority on what the token bought.
  product_id: z.string().min(1).max(128).optional(),
  purchase_token: z.string().min(10).max(4096),
});
