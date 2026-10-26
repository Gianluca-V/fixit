import { describe, expect, test, vi } from "vitest";
import { handleNotify, type Env, type NotifyDeps } from "../src/index";
import type { FcmMessage } from "../src/fcm";
import type { FetchRequestResult } from "../src/firestore";
import type { RequestDoc } from "../src/types";

const NOW = Date.parse("2026-10-08T12:00:00.000Z");
const ENV: Env = {
  FIREBASE_PROJECT_ID: "fixit-test",
  GOOGLE_SERVICE_ACCOUNT: JSON.stringify({
    client_email: "push@fixit-test.iam.gserviceaccount.com",
    private_key: "test-only-not-a-key",
  }),
};

const acceptedDoc: RequestDoc = {
  id: "r1",
  status: "ACCEPTED",
  clientId: "c1",
  professionalId: "p1",
  category: "GAS",
  createdAt: NOW - 600_000,
  acceptedAt: NOW - 5_000,
};

function deps(overrides: Partial<NotifyDeps> = {}) {
  const d = {
    verifyIdToken: vi.fn(async (jwt: string, _projectId: string) => (jwt === "good-token" ? "p1" : null)),
    fetchRequest: vi.fn(
      async (_p: string, _r: string, _t: string): Promise<FetchRequestResult> => ({ ok: true, request: acceptedDoc }),
    ),
    getAccessToken: vi.fn(async (_sa: { client_email: string; private_key: string }) => "oauth-token"),
    sendFcm: vi.fn(async (_p: string, _t: string, _m: FcmMessage) => {}),
    now: () => NOW,
    ...overrides,
  };
  return d;
}

function req(
  init: { method?: string; path?: string; body?: unknown; rawBody?: string; token?: string | null } = {},
): Request {
  const { method = "POST", path = "/notify", body = { type: "REQUEST_ACCEPTED", requestId: "r1" }, rawBody } = init;
  const token = init.token === undefined ? "good-token" : init.token;
  const headers = new Headers({ "Content-Type": "application/json" });
  if (token !== null) headers.set("Authorization", `Bearer ${token}`);
  const hasBody = method !== "GET" && method !== "HEAD";
  return new Request(`https://fixit-push.example.workers.dev${path}`, {
    method,
    headers,
    ...(hasBody ? { body: rawBody ?? JSON.stringify(body) } : {}),
  });
}

function expectNothingCalledAfterBody(d: ReturnType<typeof deps>) {
  expect(d.fetchRequest).not.toHaveBeenCalled();
  expect(d.getAccessToken).not.toHaveBeenCalled();
  expect(d.sendFcm).not.toHaveBeenCalled();
}

describe("handleNotify: check order of §14b.7", () => {
  test.each(["/", "/notify/", "/send", "/notify/extra"])("unknown route %s -> 404, whatever the method", async (path) => {
    for (const method of ["POST", "GET"]) {
      const d = deps();
      const res = await handleNotify(req({ path, method }), ENV, d);
      expect(res.status).toBe(404);
      expect(d.verifyIdToken).not.toHaveBeenCalled();
      expectNothingCalledAfterBody(d);
    }
  });

  test.each(["GET", "PUT", "DELETE", "PATCH", "OPTIONS"])("%s /notify -> 405 with Allow: POST", async (method) => {
    const d = deps();
    const res = await handleNotify(req({ method }), ENV, d);
    expect(res.status).toBe(405);
    expect(res.headers.get("Allow")).toBe("POST");
    expect(d.verifyIdToken).not.toHaveBeenCalled();
    expectNothingCalledAfterBody(d);
  });

  test.each([
    ["not JSON", "{type:"],
    ["unknown type", JSON.stringify({ type: "REQUEST_COMPLETED", requestId: "r1" })],
    ["path in requestId", JSON.stringify({ type: "NEW_REQUEST", requestId: "../users/x" })],
    ["array body", JSON.stringify(["NEW_REQUEST", "r1"])],
    ["empty body", ""],
  ])("invalid body (%s) -> 400 with any token, before checking it", async (_label, rawBody) => {
    for (const token of ["good-token", "bad-token", null]) {
      const d = deps();
      const res = await handleNotify(req({ rawBody, token }), ENV, d);
      expect(res.status).toBe(400);
      expect(d.verifyIdToken).not.toHaveBeenCalled();
      expectNothingCalledAfterBody(d);
    }
  });

  test.each([
    ["missing Authorization", null],
    ["invalid token", "bad-token"],
    ["empty bearer", ""],
  ])("%s with a valid body -> 401 without Firestore nor FCM", async (_label, token) => {
    const d = deps();
    const res = await handleNotify(req({ token }), ENV, d);
    expect(res.status).toBe(401);
    expectNothingCalledAfterBody(d);
  });

  test("a non-Bearer Authorization scheme -> 401", async () => {
    const d = deps();
    const request = req();
    request.headers.set("Authorization", "Basic good-token");
    const res = await handleNotify(request, ENV, d);
    expect(res.status).toBe(401);
    expect(d.verifyIdToken).not.toHaveBeenCalled();
    expectNothingCalledAfterBody(d);
  });

  test("the token is verified against FIREBASE_PROJECT_ID and Firestore is read with the same ID token", async () => {
    const d = deps();
    await handleNotify(req(), ENV, d);
    expect(d.verifyIdToken).toHaveBeenCalledWith("good-token", "fixit-test");
    expect(d.fetchRequest).toHaveBeenCalledWith("fixit-test", "r1", "good-token");
  });

  test("unknown request -> 404 without sending", async () => {
    const d = deps({ fetchRequest: vi.fn(async () => ({ ok: false, status: 404 }) as const) });
    const res = await handleNotify(req(), ENV, d);
    expect(res.status).toBe(404);
    expect(d.getAccessToken).not.toHaveBeenCalled();
    expect(d.sendFcm).not.toHaveBeenCalled();
  });

  test.each<[string, RequestDoc, string]>([
    ["wrong state", { ...acceptedDoc, status: "OPEN" }, "p1"],
    ["foreign caller", acceptedDoc, "p2"],
    ["missing professionalId", { ...acceptedDoc, professionalId: undefined }, "p1"],
    ["stale event", { ...acceptedDoc, acceptedAt: NOW - 120_001 }, "p1"],
    ["missing event mark", { ...acceptedDoc, acceptedAt: undefined }, "p1"],
    ["future event mark", { ...acceptedDoc, acceptedAt: NOW + 60_001 }, "p1"],
  ])("decideTarget rejection (%s) -> 403 without sending", async (_label, request, uid) => {
    const d = deps({
      verifyIdToken: vi.fn(async () => uid),
      fetchRequest: vi.fn(async () => ({ ok: true, request }) as const),
    });
    const res = await handleNotify(req(), ENV, d);
    expect(res.status).toBe(403);
    expect(d.getAccessToken).not.toHaveBeenCalled();
    expect(d.sendFcm).not.toHaveBeenCalled();
  });

  test("success -> 204 with exactly one FCM send to the topic of the re-read request", async () => {
    const d = deps();
    const res = await handleNotify(
      req({
        body: {
          type: "REQUEST_ACCEPTED",
          requestId: "r1",
          topic: "category_ELECTRICITY",
          category: "ELECTRICITY",
          clientId: "victim",
        },
      }),
      ENV,
      d,
    );
    expect(res.status).toBe(204);
    expect(await res.text()).toBe("");
    expect(d.getAccessToken).toHaveBeenCalledWith({
      client_email: "push@fixit-test.iam.gserviceaccount.com",
      private_key: "test-only-not-a-key",
    });
    expect(d.sendFcm).toHaveBeenCalledTimes(1);
    expect(d.sendFcm).toHaveBeenCalledWith("fixit-test", "oauth-token", {
      message: {
        topic: "user_c1",
        data: { type: "REQUEST_ACCEPTED", requestId: "r1" },
        android: { priority: "HIGH", ttl: "86400s" },
      },
    });
  });

  test("NEW_REQUEST success sends the category from Firestore, not from the body", async () => {
    const openDoc: RequestDoc = { id: "r1", status: "OPEN", clientId: "c1", category: "GAS", createdAt: NOW - 1_000 };
    const d = deps({
      verifyIdToken: vi.fn(async () => "c1"),
      fetchRequest: vi.fn(async () => ({ ok: true, request: openDoc }) as const),
    });
    const res = await handleNotify(
      req({ body: { type: "NEW_REQUEST", requestId: "r1", category: "ELECTRICITY", topic: "category_ELECTRICITY" } }),
      ENV,
      d,
    );
    expect(res.status).toBe(204);
    expect(d.sendFcm).toHaveBeenCalledTimes(1);
    expect(d.sendFcm).toHaveBeenCalledWith("fixit-test", "oauth-token", {
      message: {
        topic: "category_GAS",
        data: { type: "NEW_REQUEST", requestId: "r1", category: "GAS" },
        android: { priority: "NORMAL", ttl: "3600s" },
      },
    });
  });

  test("Firestore failure -> 502 without sending", async () => {
    const d = deps({ fetchRequest: vi.fn(async () => ({ ok: false, status: 502 }) as const) });
    const res = await handleNotify(req(), ENV, d);
    expect(res.status).toBe(502);
    expect(d.sendFcm).not.toHaveBeenCalled();
  });

  test("OAuth failure -> 502 without sending", async () => {
    const d = deps({
      getAccessToken: vi.fn(async () => {
        throw new Error("token endpoint down");
      }),
    });
    const res = await handleNotify(req(), ENV, d);
    expect(res.status).toBe(502);
    expect(d.sendFcm).not.toHaveBeenCalled();
  });

  test("FCM failure -> 502", async () => {
    const d = deps({
      sendFcm: vi.fn(async () => {
        throw new Error("FCM 500");
      }),
    });
    const res = await handleNotify(req(), ENV, d);
    expect(res.status).toBe(502);
    expect(d.sendFcm).toHaveBeenCalledTimes(1);
  });

  test("JWKS download failure while verifying -> 502, not 401", async () => {
    const d = deps({
      verifyIdToken: vi.fn(async () => {
        throw new Error("JWKS 503");
      }),
    });
    const res = await handleNotify(req(), ENV, d);
    expect(res.status).toBe(502);
    expectNothingCalledAfterBody(d);
  });

  test.each(["", "not json", JSON.stringify({ client_email: "x" })])(
    "a malformed GOOGLE_SERVICE_ACCOUNT secret (%j) -> 502 without sending",
    async (secret) => {
      const d = deps();
      const res = await handleNotify(req(), { ...ENV, GOOGLE_SERVICE_ACCOUNT: secret }, d);
      expect(res.status).toBe(502);
      expect(d.getAccessToken).not.toHaveBeenCalled();
      expect(d.sendFcm).not.toHaveBeenCalled();
    },
  );

  test("responses carry no CORS headers", async () => {
    const res = await handleNotify(req(), ENV, deps());
    expect(res.headers.get("Access-Control-Allow-Origin")).toBeNull();
  });
});
