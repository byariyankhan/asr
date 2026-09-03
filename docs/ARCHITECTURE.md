# Asr Architecture

## System Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        Users (Android)                           │
│                      [Asr Mobile App]                            │
└────────────────────┬────────────────────────────────────────────┘
                     │
                     │ HTTPS (REST API)
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Load Balancer (Nginx)                         │
│                  api.myasr.me → :3001                            │
└────────────────────┬────────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Backend (Next.js)                              │
│              /api/v1/* routes                                    │
│              [Authentication, Business Logic]                    │
└────────────────────┬────────────────────────────────────────────┘
                     │
          ┌──────────┴──────────┬────────────────┐
          ▼                     ▼                ▼
      ┌─────────┐         ┌─────────┐      ┌──────────┐
      │PostgreSQL          │ Redis   │      │   FCM    │
      │   (asr db)         │ (asr:*) │      │(Push)    │
      └─────────┘         └─────────┘      └──────────┘
          ▲                     ▲
          └─────────────────────┘
       (Same VPS as bookween,
        but completely isolated)
```

## Architectural Principles

### 1. Complete Isolation
- **Database**: Separate `asr` database (no foreign keys to bookween)
- **Redis**: Namespaced `asr:*` keys (no shared state)
- **Code**: Separate codebase (/opt/asr/src)
- **Deployment**: Independent docker compose service

### 2. Future Scalability
- **Vertical**: Scale within current VPS (memory/CPU upgrade)
- **Horizontal**: Move to separate VPS (copy database + Redis keys)
- **Migration**: Zero code changes when moving servers

### 3. API-First Integration
- If bookween ↔ Asr communication needed: HTTP API calls only
- No shared internal protocols
- No database-level dependencies
- Enables independent versioning

### 4. Security by Design
- Separate authentication per service
- No JWT/session sharing with bookween
- Rate limiting per endpoint
- Data encryption at rest (TBD)

## Core Domain Models

### User
```
User (Asr-specific)
  ├── id (UUID)
  ├── email (unique)
  ├── passwordHash
  ├── phoneNumber (optional)
  ├── profile
  │   ├── firstName
  │   ├── lastName
  │   ├── avatar
  │   └── timezone
  ├── settings
  │   ├── notificationPreferences
  │   ├── privacyLevel
  │   └── dataExportPreference
  ├── createdAt
  ├── updatedAt
  └── deletedAt (soft delete)
```

### Device
```
Device (tied to User)
  ├── id (UUID)
  ├── userId
  ├── deviceId (from Android)
  ├── deviceName
  ├── osVersion
  ├── appVersion
  ├── fcmToken (for push notifications)
  ├── isActive
  ├── lastSyncedAt
  ├── createdAt
  └── updatedAt
```

### InstalledApp
```
InstalledApp (apps on user's device)
  ├── id (UUID)
  ├── deviceId
  ├── appPackage (e.g., "com.instagram.android")
  ├── appName
  ├── appIcon (URL or base64)
  ├── isSystem
  ├── installDate
  └── createdAt
```

### ControlledApp
```
ControlledApp (apps user wants to limit)
  ├── id (UUID)
  ├── userId
  ├── appPackage
  ├── appName
  ├── dailyLimitMinutes
  ├── isBlocked (current state)
  ├── resetTime (e.g., "00:00")
  ├── createdAt
  └── updatedAt
```

### Commitment
```
Commitment (user's commitment promise)
  ├── id (UUID)
  ├── userId
  ├── durationDays (1, 3, 7, 14, 30)
  ├── startedAt
  ├── endsAt
  ├── status (active, completed, broken)
  ├── breakReason (if broken)
  ├── canIncreaseLimit (false during commitment)
  ├── canRemoveApps (false during commitment)
  ├── createdAt
  └── updatedAt
```

### CommitmentRule
```
CommitmentRule (apps covered in commitment)
  ├── id (UUID)
  ├── commitmentId
  ├── controlledAppId
  ├── limitAtCommitmentStart
  └── createdAt
```

### Challenge
```
Challenge (earn your time)
  ├── id (UUID)
  ├── userId
  ├── commitmentId (can be null for general challenges)
  ├── type (walk_steps, focus_session, waiting_period)
  ├── targetValue (e.g., 5000 steps)
  ├── rewardMinutes
  ├── startedAt
  ├── completedAt (null if pending)
  ├── dailyLimit (e.g., max 30 min/day)
  ├── createdAt
  └── updatedAt
```

### ChallengeReward
```
ChallengeReward (track earned time)
  ├── id (UUID)
  ├── userId
  ├── challengeId
  ├── minutesEarned
  ├── awardedAt
  ├── expiresAt (if time-limited)
  └── usedAt (if burned on extension)
```

### PartnerRelationship
```
PartnerRelationship (accountability)
  ├── id (UUID)
  ├── userId (person being held accountable)
  ├── partnerId (accountability partner)
  ├── status (pending, accepted, declined, blocked)
  ├── invitedAt
  ├── acceptedAt
  ├── settings
  │   ├── notifyOnSuccess
  │   ├── notifyOnFailure
  │   ├── viewsProgress
  │   └── canRoast (humor)
  ├── createdAt
  └── updatedAt
```

### ProtectionEvent
```
ProtectionEvent (proof of life tracking)
  ├── id (UUID)
  ├── deviceId
  ├── eventType (usage, blocking, app_open, verification_check)
  ├── appPackage (if relevant)
  ├── timestamp
  └── createdAt (indexed for pagination)
```

### UsageEvent
```
UsageEvent (screen time data)
  ├── id (UUID)
  ├── deviceId
  ├── appPackage
  ├── startTime
  ├── endTime
  ├── durationSeconds
  ├── foreground (true if actively used)
  ├── createdAt (batch inserted)
  └── updatedAt
```

### Notification
```
Notification (user notifications)
  ├── id (UUID)
  ├── userId
  ├── type (success, roast, warning, reminder)
  ├── title
  ├── body
  ├── deepLink (e.g., "/commitment/123")
  ├── isRead
  ├── sentAt
  └── createdAt
```

### Streak
```
Streak (progress tracking)
  ├── id (UUID)
  ├── userId
  ├── type (current, longest)
  ├── count (days)
  ├── startedAt
  ├── lastExtendedAt
  ├── brokenAt (if broken)
  └── createdAt
```

### Subscription
```
Subscription (payment tracking)
  ├── id (UUID)
  ├── userId
  ├── plan (free, pro, premium)
  ├── status (active, canceled, expired)
  ├── startedAt
  ├── endsAt
  ├── renewsAt
  ├── paddleCustomerId
  ├── paddleSubscriptionId
  └── createdAt
```

## Database Schema Design

### Key Principles
- **Isolation**: No foreign keys to bookween
- **Performance**: Proper indexing for common queries
- **Audit**: Created/updated timestamps on everything
- **Soft Delete**: Support user data deletion requests
- **Pagination**: timestamp + id cursor support

### Indexes
```sql
-- User authentication
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_devices_fcmToken ON devices(fcmToken);

-- App control
CREATE INDEX idx_controlled_apps_userId ON controlled_apps(userId);
CREATE INDEX idx_usage_events_deviceId_timestamp ON usage_events(deviceId, createdAt DESC);

-- Commitment tracking
CREATE INDEX idx_commitments_userId_status ON commitments(userId, status);
CREATE INDEX idx_commitment_rules_commitmentId ON commitment_rules(commitmentId);

-- Challenges
CREATE INDEX idx_challenges_userId_type ON challenges(userId, type);
CREATE INDEX idx_challenge_rewards_userId_awardedAt ON challenge_rewards(userId, awardedAt DESC);

-- Partners
CREATE INDEX idx_partner_relationships_userId ON partner_relationships(userId);
CREATE INDEX idx_partner_relationships_partnerId ON partner_relationships(partnerId);

-- Protection events (critical for verification)
CREATE INDEX idx_protection_events_deviceId_timestamp ON protection_events(deviceId, createdAt DESC);

-- Notifications
CREATE INDEX idx_notifications_userId_isRead ON notifications(userId, isRead);

-- Search
CREATE INDEX idx_installed_apps_deviceId_package ON installed_apps(deviceId, appPackage);
```

## API Architecture

### Base URL
`https://api.myasr.me`

### API Versioning
`/api/v1/*` - All endpoints versioned for future compatibility

### Authentication
- Bearer token (JWT)
- Issued at login/signup
- Refreshable
- Stored in app secure storage (not shared with bookween)

### Rate Limiting
```
Authenticated: 100 req/min per user
Public: 10 req/min per IP
Push events: 1000 req/min (internal only)
```

## Backend Services

### 1. Authentication Service
- User signup/login/logout
- Email verification
- Password reset
- Device registration
- JWT token management

### 2. App Control Service
- List installed apps
- Create/update/delete controlled apps
- Check if app is blocked
- Calculate screen time

### 3. Commitment Service
- Start commitment
- Check commitment rules
- Prevent rule violations
- Record commitment completion/failure

### 4. Challenge Service
- List available challenges
- Track progress
- Award minutes
- Apply daily limits

### 5. Partner Service
- Send invitations
- Accept/decline partnerships
- Get partner progress
- Manage partner notifications

### 6. Protection Service
- Track device protection status
- Verify protection is active
- Detect protection loss
- Schedule verification checks

### 7. Notification Service
- Send FCM messages
- Queue notifications
- Track delivery
- Manage user preferences

### 8. Analytics Service (Future)
- Track user behavior
- Measure feature adoption
- Monitor health metrics
- Generate reports

## Data Flow

### User Signup
```
1. User opens app
2. Signup → POST /api/v1/auth/signup
3. Backend creates User, Device
4. Returns JWT + FCM token endpoint
5. App stores JWT in secure storage
6. App registers FCM token
```

### Setting Limits
```
1. User selects apps → POST /api/v1/apps/controlled
2. Backend creates ControlledApp records
3. Sends verification challenge to device
4. Device acknowledges via UsageStatsManager
```

### Commitment Flow
```
1. User starts commitment → POST /api/v1/commitments
2. Backend validates no active commitment
3. Creates Commitment + CommitmentRules (locking apps/limits)
4. App receives commitment_active notification
5. Device enforces blocks locally
6. Backend tracks via ProtectionEvents
```

### Earn Your Time
```
1. User walks 5000 steps → App detects via Step Sensor
2. Challenge completion → POST /api/v1/challenges/{id}/complete
3. Backend verifies user in active commitment
4. Awards ChallengeReward (minutes)
5. Sends success notification to partners
```

### Partner Accountability
```
1. User sends invite → POST /api/v1/partners/invite
2. Partner joins via deep link
3. Both accept relationship
4. Backend subscribes partner to user's events
5. On commitment complete/break → notify partner
6. Partner can view progress dashboard
```

## Deployment Architecture

### Current Setup (Same VPS)
```
/opt/asr/
├── src/                    # Git repository
├── docker-compose.yml      # Service definition
└── .env                    # Environment variables

Services:
├── asr-web (port 3001)
├── postgres (port 5432, shared with bookween)
└── redis (port 6379, shared with bookween)

Nginx: api.myasr.me → :3001
```

### Future Separate Server
```
/opt/asr/
├── src/                    # Same git repo
├── docker-compose.yml      # Standalone compose
└── .env                    # New server vars

Services: (Same setup, different hardware)
├── asr-web (port 3001)
├── postgres (independent copy of asr database)
└── redis (independent instance)

Nginx: api.myasr.me → new.server.ip
DNS: myasr.me → new.server.ip
```

**Migration Process:**
1. Backup asr database from old server
2. Restore to new server
3. Export asr:* Redis keys → import to new Redis
4. Copy .env, docker-compose.yml
5. Start services
6. Update DNS
7. Monitor for 24h
8. Keep old server as backup for 1 week
9. Decommission

**Zero code changes required.**

## Security Considerations

### Authentication
- Passwords: bcrypt (12 rounds)
- JWT: HS256 (HMAC-SHA256)
- Token expiry: 1 week (refresh available)
- Refresh token: HttpOnly cookie (if web)

### Data Privacy
- User data: encrypted at rest (future)
- Passwords: hashed, salted
- API: HTTPS only
- Rate limiting: prevent brute force
- CORS: api.myasr.me only (for now)

### Protection Against
- SQL injection: Prepared statements (Prisma)
- XSS: Never store user data in cookies
- CSRF: State validation in auth flows
- Timing attacks: Constant-time comparisons

## Monitoring & Observability

### Metrics to Track
- API response times
- Database query performance
- FCM delivery rates
- Protection event success rate
- Device sync frequency

### Logs
- All API requests (with user ID)
- Database migrations
- FCM failures
- Scheduled job execution

### Alerts
- High error rates
- Database connection failures
- FCM service down
- Disk space critical
- Memory usage critical

## Performance Targets

### API Response Times
- Auth endpoints: <200ms
- App list: <500ms
- Commitment creation: <300ms
- Notification send: <500ms (async)

### Database
- Query timeout: 5s
- Connection pool: 10-20
- Slow query log: >1s

### Scalability
- Concurrent users: Target 1M+
- Requests per second: Target 10k+
- Database size: Can grow to 100GB+ (auto-archive old events)

## Future Enhancements

### Phase 2 (V1.1)
- iOS support
- Advanced analytics
- Social leaderboards
- Custom reward catalog

### Phase 3 (V2)
- AI-powered insights
- Integration with calendar/productivity apps
- Family/parental controls
- Workplace wellness programs

### Phase 4 (V3)
- VR/AR focused experiences
- Biometric integration
- ML-based habit prediction
- Global community features
