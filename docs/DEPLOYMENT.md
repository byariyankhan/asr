# Asr Deployment Guide

## Pre-Deployment Checklist

### Domain & DNS
- [ ] Domain `myasr.me` registered
- [ ] DNS A record pointing to 187.52.122.99
- [ ] DNS propagation verified (`nslookup myasr.me`)

### SSL Certificate
- [ ] Let's Encrypt certificate obtained for `api.myasr.me`
- [ ] Certificate configured in Nginx
- [ ] Auto-renewal tested

### Credentials & Secrets
- [ ] PostgreSQL password for `asr` user set
- [ ] Redis password configured
- [ ] JWT secret generated
- [ ] Firebase FCM credentials obtained
- [ ] Resend API key (if using email)
- [ ] Paddle API credentials (if using payments)

### Environment Setup
- [ ] `/opt/asr/.env` created with all required variables
- [ ] `.env` file never committed to git
- [ ] Secrets rotation policy documented

---

## Step 1: Database Setup

### Create Asr Database
```bash
# SSH into VPS
ssh root@187.52.122.99

# Connect to postgres
psql -U postgres

# Create database
CREATE DATABASE asr OWNER asr;
GRANT ALL PRIVILEGES ON DATABASE asr TO asr;
\q
```

### Verify Connection
```bash
psql -U asr -d asr -c "SELECT version();"
```

### Run Migrations
```bash
cd /opt/asr/src
npm run db:migrate
```

---

## Step 2: Docker Compose Setup

### Update Bookween Compose File
Edit `/opt/bookween/docker-compose.yml` to add asr service:

```yaml
services:
  # ... existing postgres, redis, web (bookween)

  asr-web:
    build:
      context: /opt/asr/src
      dockerfile: Dockerfile
      args:
        NEXT_PUBLIC_SITE_URL: ${ASR_SITE_URL}
    restart: unless-stopped
    environment:
      DATABASE_URL: postgresql://asr:${PG_PASS}@postgres:5432/asr
      REDIS_URL: redis://:${REDIS_PASS}@redis:6379/1
      NODE_ENV: production
      PORT: 3000
      HOSTNAME: 0.0.0.0
      BETTER_AUTH_SECRET: ${ASR_BETTER_AUTH_SECRET}
      BETTER_AUTH_URL: ${ASR_BETTER_AUTH_URL}
      RESEND_API_KEY: ${RESEND_API_KEY}
      FIREBASE_PROJECT_ID: ${FIREBASE_PROJECT_ID}
      FIREBASE_PRIVATE_KEY: ${FIREBASE_PRIVATE_KEY}
      FIREBASE_CLIENT_EMAIL: ${FIREBASE_CLIENT_EMAIL}
    ports:
      - "127.0.0.1:3001:3000"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    networks:
      - bookween
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:3000/api/v1/health"]
      interval: 10s
      timeout: 5s
      retries: 5
```

### Validate Compose
```bash
docker compose -f /opt/bookween/docker-compose.yml config -q
```

### Build Asr Image
```bash
cd /opt/bookween
docker compose build asr-web
```

---

## Step 3: Nginx Configuration

### Create Asr Nginx Config
Create `/etc/nginx/sites-available/asr`:

```nginx
server {
    server_name api.myasr.me www.api.myasr.me;

    client_max_body_size 6m;

    location / {
        proxy_pass http://127.0.0.1:3001;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # Timeouts
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }

    # SSL (will be added by certbot)
    listen 443 ssl http2;
    listen [::]:443 ssl http2 ipv6only=on;
    ssl_certificate /etc/letsencrypt/live/api.myasr.me/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.myasr.me/privkey.pem;
    include /etc/letsencrypt/options-ssl-nginx.conf;
    ssl_dhparam /etc/letsencrypt/ssl-dhparams.pem;
}

server {
    listen 80;
    listen [::]:80;
    server_name api.myasr.me www.api.myasr.me;
    return 301 https://$host$request_uri;
}
```

### Enable Site
```bash
sudo ln -s /etc/nginx/sites-available/asr /etc/nginx/sites-enabled/asr
sudo nginx -t
sudo systemctl reload nginx
```

### Get SSL Certificate
```bash
sudo certbot --nginx -d api.myasr.me -d www.api.myasr.me
```

---

## Step 4: Environment Variables

### Create `/opt/asr/.env`
```bash
# Build-time
NEXT_PUBLIC_SITE_URL=https://api.myasr.me
NODE_ENV=production

# Database
DATABASE_URL=postgresql://asr:${PG_PASS}@postgres:5432/asr

# Redis
REDIS_URL=redis://:${REDIS_PASS}@redis:6379/1

# Authentication
BETTER_AUTH_SECRET=<generate with: openssl rand -base64 32>
BETTER_AUTH_URL=https://api.myasr.me

# Email (Resend)
RESEND_API_KEY=<from Resend dashboard>

# Firebase (Notifications)
FIREBASE_PROJECT_ID=<from Firebase Console>
FIREBASE_PRIVATE_KEY=<from Firebase Console>
FIREBASE_CLIENT_EMAIL=<from Firebase Console>

# Optional: Analytics, Sentry, etc.
SENTRY_DSN=<optional>

# Payments (Paddle)
PADDLE_ENV=live
PADDLE_API_KEY=<from Paddle>
PADDLE_CLIENT_TOKEN=<from Paddle>
```

---

## Step 5: Deploy Services

### Start Asr Service
```bash
cd /opt/bookween
docker compose up -d asr-web
```

### Verify Container Running
```bash
docker compose ps

# Expected output:
# asr-web        running (Up about X seconds)
# postgres       running
# redis          running
# web            running
```

### Check Logs
```bash
docker compose logs -f asr-web
```

### Verify Health Check
```bash
curl http://127.0.0.1:3001/api/v1/health
```

---

## Step 6: Run Database Migrations

```bash
docker compose exec asr-web npm run db:migrate
```

### Verify Schema
```bash
psql -U asr -d asr -c "
  SELECT table_name 
  FROM information_schema.tables 
  WHERE table_schema = 'public' 
  ORDER BY table_name;
"
```

---

## Step 7: Verify Deployment

### Test API Endpoints
```bash
# Health check
curl -i https://api.myasr.me/api/v1/health

# Signup (should fail with missing fields, but endpoint exists)
curl -X POST https://api.myasr.me/api/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"test"}'
```

### Check Nginx
```bash
sudo tail -f /var/log/nginx/access.log
sudo tail -f /var/log/nginx/error.log
```

### Monitor Resources
```bash
docker stats asr-web

# Expected:
# CPU: 1-3%
# Memory: 300-500MB
```

---

## Step 8: Backup Configuration

### Database Backup
```bash
# Daily backup script
cat > /usr/local/bin/backup-asr.sh <<'EOF'
#!/bin/bash
BACKUP_DIR="/opt/backups"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="$BACKUP_DIR/asr_$TIMESTAMP.dump"

mkdir -p $BACKUP_DIR

# Backup asr database
docker compose -f /opt/bookween/docker-compose.yml exec -T postgres \
  pg_dump -U asr -d asr -F custom > $BACKUP_FILE

# Upload to R2 (optional)
# aws s3 cp $BACKUP_FILE s3://backups/asr/ --region auto

echo "Backup created: $BACKUP_FILE"
EOF

chmod +x /usr/local/bin/backup-asr.sh
```

### Add Cron Job
```bash
# Edit crontab
crontab -e

# Add line (backup daily at 2 AM):
0 2 * * * /usr/local/bin/backup-asr.sh
```

---

## Step 9: Monitoring & Alerts

### Health Check Endpoint
```bash
# Should return 200 with:
# {"status": "ok"}
curl https://api.myasr.me/api/v1/health
```

### Log Monitoring
```bash
# Watch container logs
docker compose logs -f asr-web --tail=50

# Filter errors
docker compose logs asr-web 2>&1 | grep -i error
```

### CPU/Memory Alerts
```bash
# Monitor resource usage
watch -n 5 'docker stats asr-web --no-stream'
```

---

## Post-Deployment

### ✅ Verification Checklist
- [ ] API responds to requests
- [ ] SSL certificate valid (check https://api.myasr.me)
- [ ] Database connection working
- [ ] Redis connection working
- [ ] FCM notification service working
- [ ] Email service working (test signup)
- [ ] Backups running daily
- [ ] Logs accessible
- [ ] Resource usage acceptable

### 📊 Monitoring
- Daily health checks
- Weekly performance review
- Monthly security audit
- Quarterly backup restore test

### 🔧 Maintenance
- Keep Docker images updated
- Rotate credentials quarterly
- Archive old logs monthly
- Optimize database quarterly

---

## Rollback Procedure

If deployment fails:

```bash
# Stop asr-web
docker compose stop asr-web

# Remove container
docker compose rm asr-web

# Restore database from backup
docker compose exec -T postgres \
  pg_restore -U asr -d asr /path/to/backup.dump

# Restart
docker compose up -d asr-web
```

---

## Troubleshooting

### Service won't start
```bash
docker compose logs asr-web
# Check: Database connection, environment variables, port conflicts
```

### Database migration fails
```bash
# Verify database exists and user has permissions
psql -U asr -d asr -c "SELECT 1;"

# Run migration manually
docker compose exec asr-web npx kysely migrate:latest
```

### High memory usage
```bash
# Check Node process
docker exec <container_id> ps aux
# Restart service
docker compose restart asr-web
```

### SSL certificate issues
```bash
# Verify certificate
sudo certbot certificates

# Renew manually
sudo certbot renew --force-renewal

# Check logs
sudo tail -f /var/log/letsencrypt/letsencrypt.log
```

---

## Performance Optimization (Future)

### Caching Layer
- Add Redis caching for frequently accessed data
- Cache API responses (15-60 min)
- Implement cache invalidation strategy

### Database Optimization
```sql
-- Analyze query performance
EXPLAIN ANALYZE SELECT * FROM users WHERE email = 'test@example.com';

-- Create composite indexes if needed
CREATE INDEX idx_protection_events_device_timestamp 
ON protection_events(device_id, created_at DESC);
```

### Load Testing
```bash
# Use k6 or Apache Bench
ab -n 1000 -c 100 https://api.myasr.me/api/v1/health
```

---

## Scaling to Separate Server (Future)

When traffic demands:

1. **Backup asr database**
   ```bash
   pg_dump -U asr -d asr -F custom > asr_prod.dump
   ```

2. **Export Redis keys**
   ```bash
   redis-cli -a PASSWORD KEYS "asr:*" > asr_keys.txt
   ```

3. **Set up new VPS** with same configuration

4. **Restore data**
   ```bash
   pg_restore -U asr -d asr asr_prod.dump
   redis-cli -a PASSWORD < asr_keys.txt
   ```

5. **Update DNS** - Point api.myasr.me to new IP

6. **Monitor** - Watch logs for 24 hours

**Zero code changes required** - architecture supports horizontal scaling.

