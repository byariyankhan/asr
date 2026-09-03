# Asr API Documentation

## Base URL
```
https://api.myasr.me/api/v1
```

## Authentication
All endpoints (except auth) require:
```
Authorization: Bearer <JWT_TOKEN>
```

## Response Format

### Success Response (2xx)
```json
{
  "status": "success",
  "data": { /* endpoint-specific data */ },
  "message": "Optional message"
}
```

### Error Response (4xx, 5xx)
```json
{
  "status": "error",
  "error": "error_code",
  "message": "Human readable message",
  "details": { /* optional error details */ }
}
```

## Rate Limiting
- Authenticated: 100 req/min per user
- Public: 10 req/min per IP
- Response headers:
  ```
  X-RateLimit-Limit: 100
  X-RateLimit-Remaining: 87
  X-RateLimit-Reset: 1630000000
  ```

---

## Authentication Endpoints

### POST /auth/signup
Register new user

**Request:**
```json
{
  "email": "user@example.com",
  "password": "securePassword123!",
  "firstName": "John",
  "lastName": "Doe"
}
```

**Response (201):**
```json
{
  "status": "success",
  "data": {
    "user": {
      "id": "uuid",
      "email": "user@example.com",
      "firstName": "John",
      "lastName": "Doe",
      "createdAt": "2024-09-03T10:00:00Z"
    },
    "token": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIs..."
  }
}
```

**Errors:**
- 409: Email already exists
- 400: Invalid email or password format

---

### POST /auth/login
Authenticate user

**Request:**
```json
{
  "email": "user@example.com",
  "password": "securePassword123!"
}
```

**Response (200):**
```json
{
  "status": "success",
  "data": {
    "user": { /* user object */ },
    "token": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIs..."
  }
}
```

**Errors:**
- 401: Invalid credentials
- 404: User not found

---

### POST /auth/logout
Revoke tokens (optional backend support)

**Response (200):**
```json
{
  "status": "success",
  "message": "Logged out successfully"
}
```

---

### POST /auth/refresh
Refresh JWT token

**Request:**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIs..."
}
```

**Response (200):**
```json
{
  "status": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIs..."
  }
}
```

---

### POST /auth/forgot-password
Request password reset

**Request:**
```json
{
  "email": "user@example.com"
}
```

**Response (200):**
```json
{
  "status": "success",
  "message": "Password reset email sent"
}
```

---

### POST /auth/reset-password
Reset password with token

**Request:**
```json
{
  "token": "reset_token_from_email",
  "newPassword": "newPassword123!"
}
```

**Response (200):**
```json
{
  "status": "success",
  "message": "Password reset successfully"
}
```

---

## Device Endpoints

### POST /devices/register
Register device (on app launch)

**Request:**
```json
{
  "deviceId": "android_device_id",
  "deviceName": "Pixel 6",
  "osVersion": "13",
  "appVersion": "1.0.0",
  "fcmToken": "firebase_messaging_token"
}
```

**Response (201):**
```json
{
  "status": "success",
  "data": {
    "device": {
      "id": "uuid",
      "deviceId": "android_device_id",
      "deviceName": "Pixel 6",
      "fcmToken": "firebase_messaging_token",
      "createdAt": "2024-09-03T10:00:00Z"
    }
  }
}
```

---

### PUT /devices/{deviceId}/fcm-token
Update FCM token (when it changes)

**Request:**
```json
{
  "fcmToken": "new_firebase_token"
}
```

**Response (200):**
```json
{
  "status": "success",
  "data": {
    "device": { /* updated device */ }
  }
}
```

---

### GET /devices
List user's devices

**Query Parameters:**
- `limit`: 10 (default)
- `offset`: 0 (default)

**Response (200):**
```json
{
  "status": "success",
  "data": {
    "devices": [ /* array of devices */ ],
    "total": 2,
    "limit": 10,
    "offset": 0
  }
}
```

---

## App Management Endpoints

### GET /apps/installed
List installed apps on device

**Query Parameters:**
- `deviceId`: uuid (required)
- `limit`: 50 (default)
- `offset`: 0 (default)

**Response (200):**
```json
{
  "status": "success",
  "data": {
    "apps": [
      {
        "id": "uuid",
        "appPackage": "com.instagram.android",
        "appName": "Instagram",
        "appIcon": "base64_or_url",
        "isSystem": false,
        "category": "social",
        "lastUsedAt": "2024-09-03T09:30:00Z"
      }
    ],
    "total": 45,
    "limit": 50,
    "offset": 0
  }
}
```

---

### POST /apps/controlled
Add app to control list

**Request:**
```json
{
  "appPackage": "com.instagram.android",
  "appName": "Instagram",
  "dailyLimitMinutes": 60,
  "resetTime": "00:00:00"
}
```

**Response (201):**
```json
{
  "status": "success",
  "data": {
    "app": {
      "id": "uuid",
      "appPackage": "com.instagram.android",
      "appName": "Instagram",
      "dailyLimitMinutes": 60,
      "resetTime": "00:00:00",
      "isBlocked": false,
      "earnedMinutes": 0,
      "createdAt": "2024-09-03T10:00:00Z"
    }
  }
}
```

**Errors:**
- 409: App already controlled
- 400: Cannot add during active commitment (if limit increase blocked)

---

### GET /apps/controlled
List controlled apps

**Response (200):**
```json
{
  "status": "success",
  "data": {
    "apps": [ /* array of controlled apps */ ],
    "total": 5
  }
}
```

---

### PUT /apps/controlled/{appId}
Update app limits

**Request:**
```json
{
  "dailyLimitMinutes": 45,
  "resetTime": "06:00:00"
}
```

**Response (200):**
```json
{
  "status": "success",
  "data": {
    "app": { /* updated app */ }
  }
}
```

**Errors:**
- 400: Cannot increase limit during commitment
- 404: App not found

---

### DELETE /apps/controlled/{appId}
Remove app from control

**Response (200):**
```json
{
  "status": "success",
  "message": "App removed from control"
}
```

**Errors:**
- 400: Cannot remove app during commitment
- 404: App not found

---

## Commitment Endpoints

### POST /commitments
Start a new commitment

**Request:**
```json
{
  "durationDays": 7,
  "controlledAppIds": ["uuid1", "uuid2"]
}
```

**Response (201):**
```json
{
  "status": "success",
  "data": {
    "commitment": {
      "id": "uuid",
      "durationDays": 7,
      "startedAt": "2024-09-03T10:00:00Z",
      "endsAt": "2024-09-10T10:00:00Z",
      "status": "active",
      "canIncreaseLimit": false,
      "canRemoveApps": false,
      "rules": [
        {
          "appId": "uuid1",
          "appName": "Instagram",
          "limitAtStart": 60
        }
      ]
    }
  }
}
```

**Errors:**
- 400: Already in active commitment
- 400: No apps selected

---

### GET /commitments/current
Get active commitment

**Response (200):**
```json
{
  "status": "success",
  "data": {
    "commitment": { /* commitment object */ }
  }
}
```

**Errors:**
- 404: No active commitment

---

### GET /commitments
Get commitment history

**Query Parameters:**
- `status`: active|completed|broken (optional)
- `limit`: 20 (default)
- `offset`: 0 (default)

**Response (200):**
```json
{
  "status": "success",
  "data": {
    "commitments": [ /* array of commitments */ ],
    "total": 12,
    "limit": 20,
    "offset": 0
  }
}
```

---

### POST /commitments/{commitmentId}/break
Break an active commitment

**Request:**
```json
{
  "reason": "Couldn't resist",
  "skipNotification": false
}
```

**Response (200):**
```json
{
  "status": "success",
  "data": {
    "commitment": {
      "id": "uuid",
      "status": "broken",
      "brokenAt": "2024-09-03T11:30:00Z",
      "breakReason": "Couldn't resist"
    }
  },
  "notification_sent_to": ["partner_id_1", "partner_id_2"]
}
```

**Errors:**
- 404: Commitment not found or not active
- 409: Commitment already completed

---

## Challenge Endpoints

### GET /challenges
List available challenges

**Query Parameters:**
- `type`: walk_steps|focus_session|waiting_period (optional)
- `limit`: 10 (default)

**Response (200):**
```json
{
  "status": "success",
  "data": {
    "challenges": [
      {
        "id": "uuid",
        "type": "walk_steps",
        "targetValue": 5000,
        "rewardMinutes": 15,
        "currentValue": 2340,
        "isCompleted": false,
        "dailyLimitMinutes": 30,
        "todayEarnedMinutes": 0,
        "startsAt": "2024-09-03T00:00:00Z"
      }
    ]
  }
}
```

---

### POST /challenges/{challengeId}/complete
Mark challenge complete and claim reward

**Request:**
```json
{
  "proofData": { /* optional proof object */ }
}
```

**Response (200):**
```json
{
  "status": "success",
  "data": {
    "challenge": {
      "id": "uuid",
      "isCompleted": true,
      "completedAt": "2024-09-03T15:30:00Z"
    },
    "reward": {
      "id": "uuid",
      "minutesEarned": 15,
      "awardedAt": "2024-09-03T15:30:00Z",
      "todayEarnedMinutes": 15
    }
  }
}
```

**Errors:**
- 400: Challenge already completed
- 400: Daily limit exceeded for this type
- 404: Challenge not found

---

### GET /challenges/history
Get completed challenges

**Query Parameters:**
- `limit`: 20 (default)
- `offset`: 0 (default)

**Response (200):**
```json
{
  "status": "success",
  "data": {
    "challenges": [ /* completed challenges */ ],
    "totalEarnedMinutes": 245,
    "total": 16
  }
}
```

---

## Partner Endpoints

### POST /partners/invite
Send accountability partner invitation

**Request:**
```json
{
  "email": "friend@example.com"
}
```

**Response (201):**
```json
{
  "status": "success",
  "data": {
    "relationship": {
      "id": "uuid",
      "partnerId": "uuid",
      "partnerEmail": "friend@example.com",
      "status": "pending",
      "invitedAt": "2024-09-03T10:00:00Z",
      "inviteLink": "https://myasr.me/join/partner/abc123"
    }
  }
}
```

---

### POST /partners/accept/{relationshipId}
Accept partner invitation

**Response (200):**
```json
{
  "status": "success",
  "data": {
    "relationship": {
      "id": "uuid",
      "status": "accepted",
      "acceptedAt": "2024-09-03T11:00:00Z"
    }
  }
}
```

---

### GET /partners
List user's partners

**Query Parameters:**
- `status`: pending|accepted|declined (optional)

**Response (200):**
```json
{
  "status": "success",
  "data": {
    "partners": [
      {
        "id": "uuid",
        "userId": "uuid",
        "partner": {
          "id": "uuid",
          "firstName": "Jane",
          "lastName": "Doe",
          "avatar": "url"
        },
        "status": "accepted",
        "notifyOnSuccess": true,
        "notifyOnFailure": true,
        "viewsProgress": true
      }
    ],
    "total": 3
  }
}
```

---

### GET /partners/{partnerId}/progress
View partner's progress (only if they've granted access)

**Response (200):**
```json
{
  "status": "success",
  "data": {
    "progress": {
      "partner": {
        "id": "uuid",
        "firstName": "Jane",
        "lastName": "Doe"
      },
      "currentStreak": 5,
      "longestStreak": 12,
      "successfulCommitments": 8,
      "brokenCommitments": 2,
      "totalScreenTimeSaved": 1200, /* minutes */
      "totalMinutesEarned": 345,
      "currentCommitment": { /* if active */ }
    }
  }
}
```

**Errors:**
- 404: Partner not found or access denied
- 403: Partner hasn't granted progress access

---

### PUT /partners/{relationshipId}
Update partner notification preferences

**Request:**
```json
{
  "notifyOnSuccess": true,
  "notifyOnFailure": true,
  "viewsProgress": true,
  "canRoast": true
}
```

**Response (200):**
```json
{
  "status": "success",
  "data": {
    "relationship": { /* updated relationship */ }
  }
}
```

---

### DELETE /partners/{relationshipId}
Remove partner

**Response (200):**
```json
{
  "status": "success",
  "message": "Partner removed"
}
```

---

## Protection Endpoints

### POST /protection/events
Report protection event (from device)

**Request:**
```json
{
  "eventType": "usage",
  "appPackage": "com.instagram.android",
  "eventTimestamp": "2024-09-03T10:15:00Z",
  "details": {
    "durationSeconds": 300,
    "foreground": true
  }
}
```

**Response (201):**
```json
{
  "status": "success",
  "data": {
    "event": {
      "id": "uuid",
      "eventType": "usage",
      "appPackage": "com.instagram.android",
      "createdAt": "2024-09-03T10:15:01Z"
    }
  }
}
```

---

### GET /protection/status
Get current protection status

**Response (200):**
```json
{
  "status": "success",
  "data": {
    "isProtectionActive": true,
    "lastEventAt": "2024-09-03T10:30:00Z",
    "missedVerifications": 0,
    "protectionLost": false,
    "recommendations": []
  }
}
```

---

### GET /protection/events
Get protection event history

**Query Parameters:**
- `limit`: 50 (default)
- `cursor`: timestamp_id (for pagination)
- `eventType`: optional filter

**Response (200):**
```json
{
  "status": "success",
  "data": {
    "events": [
      {
        "id": "uuid",
        "eventType": "usage",
        "appPackage": "com.instagram.android",
        "eventTimestamp": "2024-09-03T10:15:00Z",
        "createdAt": "2024-09-03T10:15:01Z"
      }
    ],
    "total": 245,
    "cursor": "2024-09-03T09:00:00Z_uuid"
  }
}
```

---

## Notification Endpoints

### GET /notifications
Get user notifications

**Query Parameters:**
- `unreadOnly`: false (default)
- `limit`: 20 (default)
- `offset`: 0 (default)

**Response (200):**
```json
{
  "status": "success",
  "data": {
    "notifications": [
      {
        "id": "uuid",
        "type": "success",
        "title": "Commitment completed!",
        "body": "You've completed your 7-day commitment. Well done!",
        "deepLink": "/commitment/abc123",
        "isRead": false,
        "sentAt": "2024-09-03T10:00:00Z"
      }
    ],
    "unreadCount": 3,
    "total": 24
  }
}
```

---

### PUT /notifications/{notificationId}/read
Mark notification as read

**Response (200):**
```json
{
  "status": "success",
  "data": {
    "notification": {
      "id": "uuid",
      "isRead": true,
      "readAt": "2024-09-03T11:00:00Z"
    }
  }
}
```

---

### PUT /notifications/read-all
Mark all notifications as read

**Response (200):**
```json
{
  "status": "success",
  "message": "All notifications marked as read"
}
```

---

## Progress Endpoints

### GET /progress
Get user progress summary

**Response (200):**
```json
{
  "status": "success",
  "data": {
    "progress": {
      "currentStreak": 5,
      "longestStreak": 12,
      "totalSuccessfulCommitments": 8,
      "totalBrokenCommitments": 2,
      "screenTimeSavedMinutes": 1260,
      "totalMinutesEarned": 345,
      "currentCommitment": {
        "id": "uuid",
        "daysRemaining": 3,
        "status": "active"
      }
    }
  }
}
```

---

### GET /progress/weekly
Get weekly progress report

**Query Parameters:**
- `week`: optional (current week by default)

**Response (200):**
```json
{
  "status": "success",
  "data": {
    "week": "2024-09-03",
    "stats": {
      "totalScreenTimeMinutes": 420,
      "controlledAppUsageMinutes": 240,
      "successDays": 5,
      "failureDays": 0,
      "challengesCompleted": 8,
      "minutesEarned": 120
    }
  }
}
```

---

## User Endpoints

### GET /user/profile
Get user profile

**Response (200):**
```json
{
  "status": "success",
  "data": {
    "user": {
      "id": "uuid",
      "email": "user@example.com",
      "firstName": "John",
      "lastName": "Doe",
      "avatar": "url",
      "timezone": "UTC",
      "createdAt": "2024-09-03T10:00:00Z"
    }
  }
}
```

---

### PUT /user/profile
Update user profile

**Request:**
```json
{
  "firstName": "John",
  "lastName": "Doe",
  "timezone": "America/New_York",
  "avatar": "base64_or_url"
}
```

**Response (200):**
```json
{
  "status": "success",
  "data": {
    "user": { /* updated user */ }
  }
}
```

---

### DELETE /user
Delete user account (soft delete)

**Request:**
```json
{
  "password": "user_password",
  "reason": "optional reason"
}
```

**Response (200):**
```json
{
  "status": "success",
  "message": "Account deleted successfully. Your data will be permanently removed after 30 days."
}
```

---

## Error Codes

| Code | Status | Meaning |
|------|--------|---------|
| `invalid_input` | 400 | Invalid request parameters |
| `unauthorized` | 401 | Missing or invalid authentication |
| `forbidden` | 403 | Insufficient permissions |
| `not_found` | 404 | Resource not found |
| `conflict` | 409 | Resource already exists or conflicts |
| `rate_limit_exceeded` | 429 | Too many requests |
| `internal_error` | 500 | Server error |

---

## Pagination

### Offset-based
```
GET /endpoint?limit=20&offset=40
```

### Cursor-based (for large datasets)
```
GET /endpoint?limit=20&cursor=2024-09-03T10:00:00Z_uuid
```

Response includes `nextCursor` if more data available.

