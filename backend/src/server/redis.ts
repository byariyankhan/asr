import Redis from "ioredis";

// One shared ioredis client. Module-level singletons survive Next's dev-mode
// module reloading only if they hang off globalThis.
const globalForRedis = globalThis as unknown as { __asrRedis?: Redis | null };

export function redis(): Redis | null {
  if (globalForRedis.__asrRedis !== undefined) return globalForRedis.__asrRedis;
  const url = process.env.REDIS_URL;
  if (!url) {
    globalForRedis.__asrRedis = null;
    return null;
  }
  const client = new Redis(url, {
    maxRetriesPerRequest: 1,
    connectTimeout: 1000,
    // Commands issued before the socket is writable wait instead of failing;
    // commandTimeout bounds how long any of them can wait.
    enableOfflineQueue: true,
    commandTimeout: 300,
  });
  // ioredis emits 'error' on every failed reconnect; unhandled, that would
  // crash the process. Callers decide what an unavailable Redis means.
  client.on("error", () => {});
  globalForRedis.__asrRedis = client;
  return client;
}

// All keys are namespaced so a shared Redis could never collide with anything
// else; the production Redis is Asr's own regardless.
export const key = (...parts: string[]) => ["asr", ...parts].join(":");
