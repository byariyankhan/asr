# Asr Database Schema

## Database: `asr`
PostgreSQL 17 on shared VPS postgres instance (isolated database from bookween)

## Schema Definition

### Users Table
```sql
CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email VARCHAR(255) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  phone_number VARCHAR(20),
  
  -- Profile
  first_name VARCHAR(100),
  last_name VARCHAR(100),
  avatar_url TEXT,
  timezone VARCHAR(50) DEFAULT 'UTC',
  
  -- Settings
  notification_preferences JSONB DEFAULT '{"email":true,"push":true,"roasts":true}',
  privacy_level VARCHAR(20) DEFAULT 'private', -- private, partners_only, public
  data_export_preference VARCHAR(20) DEFAULT 'none',
  
  -- Status
  email_verified BOOLEAN DEFAULT FALSE,
  email_verified_at TIMESTAMP,
  is_active BOOLEAN DEFAULT TRUE,
  
  -- Timestamps
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW(),
  deleted_at TIMESTAMP,
  
  INDEX idx_users_email (email),
  INDEX idx_users_created_at (created_at DESC)
);
```

### Devices Table
```sql
CREATE TABLE devices (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  
  -- Device identification
  device_id VARCHAR(255) UNIQUE NOT NULL, -- Android ID
  device_name VARCHAR(255),
  os_version VARCHAR(50),
  app_version VARCHAR(20),
  
  -- Push notifications
  fcm_token VARCHAR(255),
  fcm_token_updated_at TIMESTAMP,
  
  -- Status
  is_active BOOLEAN DEFAULT TRUE,
  last_synced_at TIMESTAMP,
  
  -- Timestamps
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW(),
  
  CONSTRAINT fk_devices_user FOREIGN KEY (user_id) REFERENCES users(id),
  INDEX idx_devices_user_id (user_id),
  INDEX idx_devices_device_id (device_id),
  INDEX idx_devices_fcm_token (fcm_token)
);
```

### Installed Apps Table
```sql
CREATE TABLE installed_apps (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
  
  -- App identification
  app_package VARCHAR(255) NOT NULL, -- e.g., "com.instagram.android"
  app_name VARCHAR(255),
  app_icon BYTEA, -- Base64 encoded or URL
  
  -- Classification
  is_system BOOLEAN DEFAULT FALSE,
  category VARCHAR(50), -- social, productivity, games, etc
  
  -- Tracking
  install_date TIMESTAMP,
  last_used_at TIMESTAMP,
  
  -- Timestamps
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW(),
  
  CONSTRAINT fk_installed_apps_device FOREIGN KEY (device_id) REFERENCES devices(id),
  INDEX idx_installed_apps_device_id (device_id),
  INDEX idx_installed_apps_package (app_package),
  UNIQUE(device_id, app_package)
);
```

### Controlled Apps Table
```sql
CREATE TABLE controlled_apps (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  
  -- App reference
  app_package VARCHAR(255) NOT NULL,
  app_name VARCHAR(255),
  
  -- Limits
  daily_limit_minutes INT DEFAULT 60, -- 0 = blocked, -1 = unlimited
  reset_time TIME DEFAULT '00:00:00', -- When daily timer resets
  
  -- Status
  is_blocked BOOLEAN DEFAULT FALSE,
  
  -- Earned time
  earned_minutes INT DEFAULT 0, -- From challenges
  
  -- Timestamps
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW(),
  deleted_at TIMESTAMP,
  
  CONSTRAINT fk_controlled_apps_user FOREIGN KEY (user_id) REFERENCES users(id),
  INDEX idx_controlled_apps_user_id (user_id),
  INDEX idx_controlled_apps_user_package (user_id, app_package),
  UNIQUE(user_id, app_package)
);
```

### Commitments Table
```sql
CREATE TABLE commitments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  
  -- Duration
  duration_days INT NOT NULL, -- 1, 3, 7, 14, 30
  started_at TIMESTAMP NOT NULL DEFAULT NOW(),
  ends_at TIMESTAMP GENERATED ALWAYS AS (started_at + (duration_days || ' days')::INTERVAL) STORED,
  
  -- Status
  status VARCHAR(20) DEFAULT 'active', -- active, completed, broken
  broken_at TIMESTAMP,
  break_reason TEXT,
  
  -- Constraints during commitment
  can_increase_limit BOOLEAN DEFAULT FALSE,
  can_remove_apps BOOLEAN DEFAULT FALSE,
  
  -- Timestamps
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW(),
  
  CONSTRAINT fk_commitments_user FOREIGN KEY (user_id) REFERENCES users(id),
  INDEX idx_commitments_user_id (user_id),
  INDEX idx_commitments_status (status),
  INDEX idx_commitments_user_status (user_id, status),
  INDEX idx_commitments_ends_at (ends_at)
);
```

### Commitment Rules Table
```sql
CREATE TABLE commitment_rules (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  commitment_id UUID NOT NULL REFERENCES commitments(id) ON DELETE CASCADE,
  controlled_app_id UUID NOT NULL REFERENCES controlled_apps(id) ON DELETE CASCADE,
  
  -- Snapshot of limit at commitment start
  limit_at_commitment_start INT,
  
  -- Timestamps
  created_at TIMESTAMP DEFAULT NOW(),
  
  CONSTRAINT fk_commitment_rules_commitment FOREIGN KEY (commitment_id) REFERENCES commitments(id),
  CONSTRAINT fk_commitment_rules_app FOREIGN KEY (controlled_app_id) REFERENCES controlled_apps(id),
  INDEX idx_commitment_rules_commitment_id (commitment_id),
  INDEX idx_commitment_rules_app_id (controlled_app_id)
);
```

### Challenges Table
```sql
CREATE TABLE challenges (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  commitment_id UUID REFERENCES commitments(id) ON DELETE SET NULL, -- Can be NULL for non-commitment challenges
  
  -- Challenge type
  type VARCHAR(50) NOT NULL, -- walk_steps, focus_session, waiting_period, meditation
  target_value INT, -- Steps, minutes, etc
  reward_minutes INT DEFAULT 15,
  
  -- Progress
  current_value INT DEFAULT 0,
  is_completed BOOLEAN DEFAULT FALSE,
  completed_at TIMESTAMP,
  
  -- Daily limit (user can't earn more than X min/day)
  daily_limit_minutes INT DEFAULT 30,
  today_earned_minutes INT DEFAULT 0,
  
  -- Timestamps
  started_at TIMESTAMP DEFAULT NOW(),
  expires_at TIMESTAMP, -- Optional expiration
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW(),
  
  CONSTRAINT fk_challenges_user FOREIGN KEY (user_id) REFERENCES users(id),
  INDEX idx_challenges_user_id (user_id),
  INDEX idx_challenges_commitment_id (commitment_id),
  INDEX idx_challenges_type (type),
  INDEX idx_challenges_expires_at (expires_at)
);
```

### Challenge Rewards Table
```sql
CREATE TABLE challenge_rewards (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  challenge_id UUID NOT NULL REFERENCES challenges(id) ON DELETE CASCADE,
  
  -- Reward details
  minutes_earned INT NOT NULL,
  reason VARCHAR(255), -- What earned this reward
  
  -- Status
  is_used BOOLEAN DEFAULT FALSE,
  used_at TIMESTAMP,
  
  -- Expiration (if time-limited)
  expires_at TIMESTAMP,
  
  -- Timestamps
  awarded_at TIMESTAMP DEFAULT NOW(),
  created_at TIMESTAMP DEFAULT NOW(),
  
  CONSTRAINT fk_challenge_rewards_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_challenge_rewards_challenge FOREIGN KEY (challenge_id) REFERENCES challenges(id),
  INDEX idx_challenge_rewards_user_id (user_id),
  INDEX idx_challenge_rewards_awarded_at (awarded_at DESC),
  INDEX idx_challenge_rewards_expires_at (expires_at)
);
```

### Partner Relationships Table
```sql
CREATE TABLE partner_relationships (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE, -- Person being held accountable
  partner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE, -- Accountability partner
  
  -- Status
  status VARCHAR(20) DEFAULT 'pending', -- pending, accepted, declined, blocked
  invited_at TIMESTAMP DEFAULT NOW(),
  accepted_at TIMESTAMP,
  
  -- Preferences
  notify_on_success BOOLEAN DEFAULT TRUE,
  notify_on_failure BOOLEAN DEFAULT TRUE,
  views_progress BOOLEAN DEFAULT TRUE,
  can_roast BOOLEAN DEFAULT FALSE,
  
  -- Timestamps
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW(),
  deleted_at TIMESTAMP,
  
  CONSTRAINT fk_partner_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_partner_partner FOREIGN KEY (partner_id) REFERENCES users(id),
  INDEX idx_partner_user_id (user_id),
  INDEX idx_partner_partner_id (partner_id),
  INDEX idx_partner_status (status),
  UNIQUE(user_id, partner_id) -- Can't add same partner twice
);
```

### Protection Events Table
```sql
CREATE TABLE protection_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
  
  -- Event type
  event_type VARCHAR(50) NOT NULL, -- usage, blocking, app_open, manual_verification, scheduled_check
  app_package VARCHAR(255), -- If relevant to event
  
  -- Event details
  details JSONB, -- Flexible event data
  
  -- Timestamps (critical for ordering)
  event_timestamp TIMESTAMP NOT NULL,
  created_at TIMESTAMP DEFAULT NOW(),
  
  CONSTRAINT fk_protection_events_device FOREIGN KEY (device_id) REFERENCES devices(id),
  INDEX idx_protection_events_device_id (device_id),
  INDEX idx_protection_events_timestamp (event_timestamp DESC),
  INDEX idx_protection_events_device_timestamp (device_id, event_timestamp DESC)
);
```

### Usage Events Table
```sql
CREATE TABLE usage_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
  
  -- App usage
  app_package VARCHAR(255) NOT NULL,
  start_time TIMESTAMP NOT NULL,
  end_time TIMESTAMP NOT NULL,
  duration_seconds INT NOT NULL,
  
  -- Context
  foreground BOOLEAN DEFAULT TRUE, -- Was app in foreground?
  
  -- Timestamps
  created_at TIMESTAMP DEFAULT NOW(),
  
  CONSTRAINT fk_usage_events_device FOREIGN KEY (device_id) REFERENCES devices(id),
  INDEX idx_usage_events_device_id (device_id),
  INDEX idx_usage_events_app_package (app_package),
  INDEX idx_usage_events_device_time (device_id, start_time DESC),
  INDEX idx_usage_events_created_at (created_at DESC)
);
```

### Notifications Table
```sql
CREATE TABLE notifications (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  
  -- Content
  type VARCHAR(50) NOT NULL, -- success, roast, warning, reminder, milestone
  title VARCHAR(255) NOT NULL,
  body TEXT,
  deep_link VARCHAR(500), -- e.g., "/commitment/123" or "/partners/456"
  
  -- Status
  is_read BOOLEAN DEFAULT FALSE,
  read_at TIMESTAMP,
  
  -- Delivery
  sent_at TIMESTAMP DEFAULT NOW(),
  fcm_message_id VARCHAR(255),
  delivery_status VARCHAR(20) DEFAULT 'pending', -- pending, sent, failed
  
  -- Timestamps
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW(),
  
  CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id),
  INDEX idx_notifications_user_id (user_id),
  INDEX idx_notifications_is_read (is_read),
  INDEX idx_notifications_created_at (created_at DESC),
  INDEX idx_notifications_user_read (user_id, is_read)
);
```

### Streaks Table
```sql
CREATE TABLE streaks (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  
  -- Streak type
  type VARCHAR(50) NOT NULL, -- current, longest, commitment_success
  
  -- Progress
  count INT DEFAULT 0,
  started_at TIMESTAMP DEFAULT NOW(),
  last_extended_at TIMESTAMP DEFAULT NOW(),
  broken_at TIMESTAMP,
  
  -- Timestamps
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW(),
  
  CONSTRAINT fk_streaks_user FOREIGN KEY (user_id) REFERENCES users(id),
  INDEX idx_streaks_user_id (user_id),
  INDEX idx_streaks_type (type),
  UNIQUE(user_id, type)
);
```

### Subscriptions Table
```sql
CREATE TABLE subscriptions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  
  -- Plan
  plan VARCHAR(50) DEFAULT 'free', -- free, pro, premium
  status VARCHAR(20) DEFAULT 'active', -- active, canceled, expired
  
  -- Dates
  started_at TIMESTAMP DEFAULT NOW(),
  ends_at TIMESTAMP,
  renews_at TIMESTAMP,
  canceled_at TIMESTAMP,
  
  -- Payment
  paddle_customer_id VARCHAR(255),
  paddle_subscription_id VARCHAR(255),
  
  -- Timestamps
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW(),
  
  CONSTRAINT fk_subscriptions_user FOREIGN KEY (user_id) REFERENCES users(id),
  INDEX idx_subscriptions_user_id (user_id),
  INDEX idx_subscriptions_status (status),
  INDEX idx_subscriptions_renews_at (renews_at)
);
```

## Migration Files

### Migration Pattern
All migrations in `/apps/web/drizzle/` using Drizzle ORM for versioning and rollback.

Example migration:
```sql
-- drizzle/0001_initial_schema.sql
CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email VARCHAR(255) UNIQUE NOT NULL,
  -- ...
);
```

## Backup Strategy

### Daily Backups
```bash
# Full backup of asr database
pg_dump -U asr -d asr -F custom -f /backups/asr_$(date +%Y%m%d_%H%M%S).dump

# Upload to R2
aws s3 cp /backups/asr_*.dump s3://backups/asr/
```

### Restore Process
```bash
# From backup
pg_restore -U asr -d asr /backups/asr_20240903_120000.dump

# Verify integrity
SELECT COUNT(*) FROM users;
```

## Performance Optimization

### Query Performance
- Use indexes on foreign keys, status fields, timestamps
- Paginate using cursor-based pagination (timestamp + id)
- Avoid n+1 queries (use JOINs)
- Analyze slow queries: `EXPLAIN ANALYZE`

### Maintenance
```sql
-- Regular maintenance
VACUUM ANALYZE;

-- Index bloat check
SELECT schemaname, tablename, indexname, idx_blks_read, idx_blks_hit 
FROM pg_statio_user_indexes 
ORDER BY idx_blks_read DESC;
```

## Archive Strategy (Future)

When tables get too large:
```sql
-- Archive old usage events (>1 year)
CREATE TABLE usage_events_archive_2023 AS 
SELECT * FROM usage_events 
WHERE created_at < '2024-01-01';

DELETE FROM usage_events 
WHERE created_at < '2024-01-01';
```

## Data Retention Policy

- **Active data**: Keep indefinitely
- **Deleted user data**: Hard delete after 30 days (GDPR)
- **Usage events**: Keep 2 years, then archive
- **Notification logs**: Keep 6 months
- **Protection events**: Keep 1 year (for verification audits)
