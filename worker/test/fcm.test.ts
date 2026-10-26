import { describe, expect, test, vi } from "vitest";
import { buildFcmMessage, sendFcm } from "../src/fcm";
import type { PushData } from "../src/types";

const newRequest: PushData = { type: "NEW_REQUEST", requestId: "r1", category: "GAS" };
const accepted: PushData = { type: "REQUEST_ACCEPTED", requestId: "r1" };
const cancelled: PushData = { type: "REQUEST_CANCELLED", requestId: "r1" };

describe("buildFcmMessage: priority and TTL per event type", () => {
  test("NEW_REQUEST -> NORMAL / 3600s", () => {
    expect(buildFcmMessage("category_GAS", newRequest)).toEqual({
      message: {
        topic: "category_GAS",
        data: { type: "NEW_REQUEST", requestId: "r1", category: "GAS" },
        android: { priority: "NORMAL", ttl: "3600s" },
      },
    });
  });

  test("REQUEST_ACCEPTED -> HIGH / 86400s", () => {
    expect(buildFcmMessage("user_c1", accepted)).toEqual({
      message: {
        topic: "user_c1",
        data: { type: "REQUEST_ACCEPTED", requestId: "r1" },
        android: { priority: "HIGH", ttl: "86400s" },
      },
    });
  });

  test("REQUEST_CANCELLED -> HIGH / 86400s", () => {
    expect(buildFcmMessage("user_p1", cancelled)).toEqual({
      message: {
        topic: "user_p1",
        data: { type: "REQUEST_CANCELLED", requestId: "r1" },
        android: { priority: "HIGH", ttl: "86400s" },
      },
    });
  });

  test("no message carries a notification block (data-only)", () => {
    for (const [topic, data] of [
      ["category_GAS", newRequest],
      ["user_c1", accepted],
      ["user_p1", cancelled],
    ] as const) {
      const built = buildFcmMessage(topic, data);
      expect(built.message).not.toHaveProperty("notification");
      expect(built.message.android).not.toHaveProperty("notification");
      expect(JSON.stringify(built)).not.toContain("notification");
    }
  });
});

describe("sendFcm", () => {
  test("POSTs the message to the v1 send endpoint with a Bearer token", async () => {
    const fetchFn = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) =>
      Response.json({ name: "projects/fixit-test/messages/123" }),
    );
    const message = buildFcmMessage("user_c1", accepted);
    await sendFcm("fixit-test", "oauth-token", message, fetchFn as typeof fetch);
    expect(fetchFn).toHaveBeenCalledTimes(1);
    const [url, init] = fetchFn.mock.calls[0] ?? [];
    expect(url).toBe("https://fcm.googleapis.com/v1/projects/fixit-test/messages:send");
    expect(init?.method).toBe("POST");
    const headers = new Headers(init?.headers);
    expect(headers.get("Authorization")).toBe("Bearer oauth-token");
    expect(headers.get("Content-Type")).toBe("application/json");
    expect(JSON.parse(String(init?.body))).toEqual(message);
  });

  test.each([400, 401, 403, 404, 429, 500, 503])("a %i response fails", async (status) => {
    const fetchFn = vi.fn(async () => new Response("error", { status }));
    await expect(
      sendFcm("fixit-test", "t", buildFcmMessage("user_c1", accepted), fetchFn as unknown as typeof fetch),
    ).rejects.toThrow();
  });
});
