# Asr - Screen Time Protection Platform

**Asr** is a commitment-based screen time management app inspired by Surah Al-Asr (The Time) from the Quran. The app helps users protect their time, maintain focus, and stay accountable to themselves and their partners.

## Tagline
**Protect Your Time. Earn Your Focus.**

## Core Philosophy

Time is finite and precious. Asr empowers users to:
- **Set limits** on distracting apps
- **Lock their commitment** to maintain discipline
- **Earn more time** through focus challenges
- **Stay accountable** through partner oversight

## Target Market

- Primary: India (Android-first)
- Secondary: Global expansion (iOS later)
- Demographics: College students, young professionals, anyone struggling with screen addiction

## Product Vision

### For Users
A spiritual + practical tool to reclaim their time. Not punishment, but empowerment through accountability and meaningful challenges.

### For Partners/Accountability
Enable others to genuinely support their friends' digital wellness journey without intrusion or judgment.

## Core Features

### Set Your Limits
- Select which apps to control
- Set daily time limits
- Custom reset times (not just midnight)

### Lock Your Commitment
- Choose commitment duration: 24h, 3d, 7d, 14d, 30d
- During commitment: limits cannot be increased, controlled apps cannot be removed
- Early break = failure recorded (no judgment, just visibility)

### Earn Your Time
- Walk steps → earn minutes
- Focus sessions → earn minutes
- Waiting periods → earn minutes
- Daily reward limits
- Challenge rules lock when commitment starts

### Accountability Partners
- Invite partners via link
- Deep linking: app install / account creation
- Multiple partners support
- Partner dashboard: see user's progress
- Notifications: success celebrations + gentle roasts on failure
- Notification preferences: customizable alerts

### Protection Verification
- Usage events = proof of life
- Blocking events = proof of life
- Manual app opens = proof of life
- Scheduled verification checks
- Multiple missed verification = protection lost detection
- FCM + device state as supporting signals

### Progress Tracking
- Current streak
- Longest streak
- Successful commitment days
- Broken commitments
- Screen time saved (calculated)
- Earned minutes (accumulated)
- Challenge history
- Weekly/monthly trends

### Production Polish
- Smooth onboarding
- Permission education
- Dark/light UI
- Smooth animations
- Empty/error/offline states
- Notification center
- Settings
- Account deletion
- Privacy/export controls

## Technical Stack

### Backend
- **Framework**: Next.js (API routes)
- **Database**: PostgreSQL (separate from bookween)
- **Cache**: Redis (isolated namespace: `asr:*`)
- **Authentication**: Better Auth
- **Notifications**: Firebase Cloud Messaging (FCM)
- **Storage**: Optional file uploads to R2

### Android
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Usage Stats**: UsageStatsManager (for app monitoring)
- **Accessibility Service**: For real-time app blocking
- **Local Storage**: Encrypted datastore
- **Background**: WorkManager for scheduled verification

### iOS (Future - V1.1+)
- **Language**: Swift
- **UI Framework**: SwiftUI
- **Screen Time API**: Limited access (Apple's restrictions)
- **Equivalent**: Notification-based approach for time tracking

## Development Phases

### Phase 1: Backend Foundation (Week 1-2)
- [ ] Database schema
- [ ] Authentication system
- [ ] Basic CRUD APIs
- [ ] Notification infrastructure

### Phase 2: Core Features (Week 3-4)
- [ ] App control system
- [ ] Commitment logic
- [ ] Usage tracking
- [ ] Accountability partners

### Phase 3: Advanced Features (Week 5-6)
- [ ] Earn your time challenges
- [ ] Progress analytics
- [ ] Partner dashboard
- [ ] Protection verification

### Phase 4: Android App (Week 7-10)
- [ ] Project setup
- [ ] UI implementation
- [ ] UsageStatsManager integration
- [ ] Real-time blocking

### Phase 5: Polish & Launch (Week 11-12)
- [ ] Testing & QA
- [ ] Performance optimization
- [ ] App store submission
- [ ] Production deployment

## Important Notes

### Production V1 Standards
- **No throwaway code** - everything production-ready from day 1
- **No fake data** - real user data from launch
- **No planned rewrites** - architecture solid for V1.1, V2 evolution
- **Future-proof** - architecture supports iOS, multiple platforms, advanced features

### Architecture Decisions
- **Separate Database**: Asr database completely independent (can move to separate server anytime)
- **Isolated Redis**: All Asr keys namespaced `asr:*` (no dependencies on bookween)
- **API-First**: Future bookween ↔ Asr integration via HTTP APIs only
- **Stateless Services**: Can scale horizontally independently

### Privacy First
- **Minimal Data Collection**: Only usage counts (not content)
- **No Content Scanning**: App names and usage times only
- **User Control**: Export/delete data anytime
- **Transparent**: Clear privacy policy from day 1

## Deployment

### Infrastructure
- **VPS**: Same server as bookween (initially)
- **Port**: 3001
- **Domain**: api.myasr.me
- **SSL**: Let's Encrypt
- **Monitoring**: Health checks, error tracking

### Future Scaling
- Move to separate VPS when traffic demands
- Add caching layer (Redis optimization)
- Database replication for HA
- Multi-region deployment (later)

## Getting Started

See [DEVELOPMENT.md](./docs/DEVELOPMENT.md) for local setup.

## API Documentation

See [API.md](./docs/API.md) for complete endpoint documentation.

## Database Schema

See [DATABASE.md](./docs/DATABASE.md) for schema details.

## Architecture

See [ARCHITECTURE.md](./docs/ARCHITECTURE.md) for system design.

## Contributing

See [CONTRIBUTING.md](./docs/CONTRIBUTING.md) for contribution guidelines.

## License

Proprietary - Ariyan Khan Productions

## Contact

- Founder: Ariyan Khan
- Email: hi@ariyankhan.com
- Website: https://ariyankhan.com
