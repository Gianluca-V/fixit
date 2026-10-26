import { defineConfig } from "vitest/config";

// Plain Node environment: the code under test only needs the global WebCrypto and
// an injected fetch/clock, so no Miniflare or workers pool (D23).
export default defineConfig({
  test: {
    environment: "node",
    include: ["test/**/*.test.ts"],
  },
});
