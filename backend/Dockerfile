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

# Next's standalone tracer copies @swc/helpers without its esm/ subfolder when
# the package is reached through pnpm's symlinked store, and next's
# require-hook then dies at boot on _interop_require_default.js. Confirmed
# here the hard way: the container started, exited MODULE_NOT_FOUND, and
# /v1/health never answered. Copy esm/ back for every version present, then
# assert the exact file Next asked for exists — a silent no-op glob would put
# the same crash back into production.
RUN set -eu; \
    for dest in .next/standalone/node_modules/.pnpm/@swc+helpers@*/node_modules/@swc/helpers; do \
      pkg=$(echo "$dest" | sed "s#.*/\.pnpm/\([^/]*\)/.*#\1#"); \
      cp -r "node_modules/.pnpm/$pkg/node_modules/@swc/helpers/esm" "$dest/esm"; \
      echo "patched $dest"; \
    done; \
    ls .next/standalone/node_modules/.pnpm/@swc+helpers@*/node_modules/@swc/helpers/esm/_interop_require_default.js

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
# The commit this image was built from, baked into the image itself rather
# than handed to the container at start: /v1/health reports it, so a running
# container can only claim a commit it was really built from, and a kept
# image (asr-api:deploy-<sha>) can say what it is before rollback.sh starts
# it. Declared last so a new commit invalidates none of the layers above;
# ENV and LABEL are metadata, not layers.
ARG ASR_COMMIT=""
ENV ASR_COMMIT=$ASR_COMMIT
LABEL io.joinasr.commit=$ASR_COMMIT
CMD ["node", "server.js"]
