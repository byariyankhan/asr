import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";

// `src/**/*.test.ts` are pure unit tests (no database). `test/**/*.test.ts`
// are integration tests against a real Postgres and skip themselves unless
// DATABASE_URL is set, so `pnpm test` is safe in CI without a database and
// thorough with one.
export default defineConfig({
  resolve: {
    alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) },
  },
  test: {
    environment: "node",
    include: ["src/**/*.test.ts", "test/**/*.test.ts"],
    fileParallelism: false,
  },
});
