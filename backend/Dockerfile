# syntax=docker/dockerfile:1
# Build context is backend/ (see infra/docker-compose.yml).
FROM node:22-alpine AS base
RUN corepack enable

FROM base AS deps
WORKDIR /app
# pnpm 11 keeps settings in pnpm-workspace.yaml, not package.json. Ours lists
# the four dependencies whose install scripts are approved to run (esbuild
# fetches its platform binary, unrs-resolver builds a native binding); without
# that file pnpm refuses to silently skip them and exits 1, which is exactly
# how this stage failed the first time it ran on the VPS.
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

# `builder` is also the image for the one-off `migrate` service: it has the
# full source and devDependencies (tsx, kysely) that the migration runner needs.
FROM base AS builder
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN pnpm build

FROM base AS runner
WORKDIR /app
ENV NODE_ENV=production
RUN addgroup --system --gid 1001 nodejs && adduser --system --uid 1001 nextjs
# API only: no public/ directory to copy.
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static
USER nextjs
EXPOSE 3000
ENV PORT=3000
ENV HOSTNAME=0.0.0.0
# alpine has wget (busybox) but not curl.
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD wget -qO- http://127.0.0.1:3000/v1/health >/dev/null 2>&1 || exit 1
CMD ["node", "server.js"]
