import { sql, type Kysely } from "kysely";

// The Asr ledger. DDL mirrors docs/DATABASE.md; keep the two in step.
export async function up(db: Kysely<unknown>): Promise<void> {
  await sql`
    alter table "user"
      add column timezone text not null default 'UTC',
      add column notify_email boolean not null default true,
      add column notify_push boolean not null default true,
      add column deleted_at timestamptz
  `.execute(db);

  await sql`
    create table device (
      id                 uuid primary key,
      user_id            text not null references "user"(id) on delete cascade,
      install_id         text not null,
      model              text,
      os_version         text,
      app_version        text not null,
      fcm_token          text,
      fcm_token_invalid  boolean not null default false,
      protection_enabled boolean not null default false,
      last_heartbeat_at  timestamptz,
      created_at         timestamptz not null default now(),
      updated_at         timestamptz not null default now(),
      unique (user_id, install_id)
    )
  `.execute(db);
  await sql`create index device_user_idx on device (user_id)`.execute(db);
  await sql`create index device_heartbeat_idx on device (last_heartbeat_at)`.execute(db);

  await sql`
    create table commitment (
      id            uuid primary key,
      user_id       text not null references "user"(id) on delete cascade,
      device_id     uuid references device(id) on delete set null,
      duration_days integer not null check (duration_days between 1 and 90),
      timezone      text not null,
      starts_at     timestamptz not null default now(),
      ends_at       timestamptz not null,
      status        text not null default 'active'
                    check (status in ('active', 'completed', 'broken')),
      ended_at      timestamptz,
      snapshot      jsonb not null,
      created_at    timestamptz not null default now(),
      updated_at    timestamptz not null default now()
    )
  `.execute(db);
  await sql`create index commitment_user_status_idx on commitment (user_id, status)`.execute(db);
  await sql`create index commitment_active_ends_idx on commitment (ends_at) where status = 'active'`.execute(db);
  await sql`create unique index commitment_one_active_idx on commitment (user_id) where status = 'active'`.execute(db);

  await sql`
    create table commitment_event (
      id              uuid primary key,
      commitment_id   uuid not null references commitment(id) on delete cascade,
      device_id       uuid references device(id) on delete set null,
      type            text not null check (type in (
                        'started', 'completed', 'broken',
                        'protection_lost', 'uninstalled', 'restored',
                        'limit_hit', 'challenge_completed', 'challenge_failed'
                      )),
      reason          text check (reason in (
                        'limit_exceeded', 'app_removed', 'protection_disabled',
                        'permission_revoked', 'heartbeat_timeout', 'fcm_unregistered',
                        'user_gave_up', 'deadline_passed'
                      )),
      app_package     text,
      minutes         integer,
      occurred_at     timestamptz not null,
      received_at     timestamptz not null default now(),
      source          text not null check (source in ('device', 'server')),
      created_at      timestamptz not null default now()
    )
  `.execute(db);
  await sql`create index commitment_event_commitment_idx on commitment_event (commitment_id, received_at desc)`.execute(db);

  await sql`
    create table challenge (
      id             uuid primary key,
      commitment_id  uuid not null references commitment(id) on delete cascade,
      user_id        text not null references "user"(id) on delete cascade,
      type           text not null check (type in ('walk_steps', 'focus_session', 'waiting_period')),
      target         integer not null,
      reward_min     integer not null,
      started_at     timestamptz not null,
      deadline_at    timestamptz not null,
      status         text not null default 'pending'
                     check (status in ('pending', 'completed', 'failed', 'cancelled')),
      ended_at       timestamptz,
      created_at     timestamptz not null default now(),
      updated_at     timestamptz not null default now()
    )
  `.execute(db);
  await sql`create index challenge_pending_deadline_idx on challenge (deadline_at) where status = 'pending'`.execute(db);
  await sql`create index challenge_commitment_idx on challenge (commitment_id)`.execute(db);

  await sql`
    create table witness (
      id                uuid primary key,
      user_id           text not null references "user"(id) on delete cascade,
      witness_user_id   text references "user"(id) on delete cascade,
      invite_code       text not null unique,
      invite_email      text,
      status            text not null default 'invited'
                        check (status in ('invited', 'accepted', 'declined', 'removed')),
      notify_start      boolean not null default true,
      notify_success    boolean not null default true,
      notify_failure    boolean not null default true,
      notify_digest     boolean not null default false,
      roast_mode        boolean not null default false,
      views_progress    boolean not null default true,
      invited_at        timestamptz not null default now(),
      responded_at      timestamptz,
      created_at        timestamptz not null default now(),
      updated_at        timestamptz not null default now()
    )
  `.execute(db);
  await sql`create index witness_user_idx on witness (user_id, status)`.execute(db);
  await sql`create index witness_witness_idx on witness (witness_user_id, status)`.execute(db);
  await sql`
    create unique index witness_pair_idx on witness (user_id, witness_user_id)
      where witness_user_id is not null and status = 'accepted'
  `.execute(db);

  await sql`
    create table notification (
      id             uuid primary key,
      recipient_id   text not null references "user"(id) on delete cascade,
      about_user_id  text references "user"(id) on delete set null,
      event_id       uuid references commitment_event(id) on delete set null,
      channel        text not null check (channel in ('push', 'email')),
      kind           text not null,
      title          text not null,
      body           text not null,
      deep_link      text,
      status         text not null default 'queued'
                     check (status in ('queued', 'sent', 'failed', 'unregistered')),
      provider_id    text,
      error          text,
      sent_at        timestamptz,
      read_at        timestamptz,
      created_at     timestamptz not null default now()
    )
  `.execute(db);
  await sql`create index notification_recipient_idx on notification (recipient_id, created_at desc)`.execute(db);
  await sql`create index notification_queued_idx on notification (created_at) where status = 'queued'`.execute(db);
  await sql`
    create unique index notification_dedupe_idx on notification (recipient_id, event_id, channel)
      where event_id is not null
  `.execute(db);

  await sql`
    create table subscription (
      id               uuid primary key,
      user_id          text not null references "user"(id) on delete cascade,
      product_id       text not null,
      purchase_token   text not null unique,
      status           text not null check (status in ('active', 'grace', 'on_hold', 'paused', 'cancelled', 'expired')),
      expires_at       timestamptz,
      last_verified_at timestamptz not null default now(),
      raw              jsonb not null,
      created_at       timestamptz not null default now(),
      updated_at       timestamptz not null default now()
    )
  `.execute(db);
  await sql`create index subscription_user_idx on subscription (user_id)`.execute(db);

  await sql`
    create table daily_summary (
      commitment_id    uuid not null references commitment(id) on delete cascade,
      day              date not null,
      app_package      text not null,
      minutes_used     integer not null,
      limit_min        integer not null,
      earned_min       integer not null default 0,
      received_at      timestamptz not null default now(),
      primary key (commitment_id, day, app_package)
    )
  `.execute(db);
}

export async function down(db: Kysely<unknown>): Promise<void> {
  for (const table of [
    "daily_summary",
    "subscription",
    "notification",
    "witness",
    "challenge",
    "commitment_event",
    "commitment",
    "device",
  ]) {
    await db.schema.dropTable(table).execute();
  }
  await sql`
    alter table "user"
      drop column timezone,
      drop column notify_email,
      drop column notify_push,
      drop column deleted_at
  `.execute(db);
}
