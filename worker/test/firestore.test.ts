import { describe, expect, test, vi } from "vitest";
import { fetchRequest, parseTimestamp } from "../src/firestore";

const URL_R1 = "https://firestore.googleapis.com/v1/projects/fixit-test/databases/(default)/documents/requests/r1";

function doc(fields: Record<string, unknown>) {
  return { name: "projects/fixit-test/databases/(default)/documents/requests/r1", fields };
}

const acceptedFields = {
  status: { stringValue: "ACCEPTED" },
  clientId: { stringValue: "c1" },
  professionalId: { stringValue: "p1" },
  category: { stringValue: "GAS" },
  description: { stringValue: "private text the Worker must not need" },
  createdAt: { timestampValue: "2026-10-08T12:30:00Z" },
  acceptedAt: { timestampValue: "2026-10-08T12:34:56.123456789Z" },
};

function fetchReturning(response: Response) {
  return vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => response);
}

describe("fetchRequest", () => {
  test("GETs the request document with the user's ID token as Bearer", async () => {
    const fetchFn = fetchReturning(Response.json(doc(acceptedFields)));
    await fetchRequest("fixit-test", "r1", "user-id-token", fetchFn as typeof fetch);
    expect(fetchFn).toHaveBeenCalledTimes(1);
    const [url, init] = fetchFn.mock.calls[0] ?? [];
    expect(url).toBe(URL_R1);
    expect(init?.method ?? "GET").toBe("GET");
    expect(new Headers(init?.headers).get("Authorization")).toBe("Bearer user-id-token");
  });

  test("maps typed fields to the request, timestamps to epoch ms", async () => {
    const fetchFn = fetchReturning(Response.json(doc(acceptedFields)));
    await expect(fetchRequest("fixit-test", "r1", "t", fetchFn as typeof fetch)).resolves.toEqual({
      ok: true,
      request: {
        id: "r1",
        status: "ACCEPTED",
        clientId: "c1",
        professionalId: "p1",
        category: "GAS",
        createdAt: Date.parse("2026-10-08T12:30:00.000Z"),
        acceptedAt: Date.parse("2026-10-08T12:34:56.123Z"),
        cancelledAt: undefined,
      },
    });
  });

  test("absent or null professionalId -> undefined", async () => {
    const { professionalId: _p, acceptedAt: _a, ...open } = acceptedFields;
    const absent = await fetchRequest(
      "fixit-test",
      "r1",
      "t",
      fetchReturning(Response.json(doc({ ...open, status: { stringValue: "OPEN" } }))) as typeof fetch,
    );
    expect(absent.ok && absent.request.professionalId).toBeUndefined();
    const nulled = await fetchRequest(
      "fixit-test",
      "r1",
      "t",
      fetchReturning(Response.json(doc({ ...open, professionalId: { nullValue: null } }))) as typeof fetch,
    );
    expect(nulled.ok && nulled.request.professionalId).toBeUndefined();
  });

  test("404 -> not_found", async () => {
    const fetchFn = fetchReturning(Response.json({ error: { code: 404 } }, { status: 404 }));
    await expect(fetchRequest("fixit-test", "r1", "t", fetchFn as typeof fetch)).resolves.toEqual({
      ok: false,
      status: 404,
    });
  });

  test.each([401, 403, 429, 500, 503])("HTTP %i -> failure 502", async (status) => {
    const fetchFn = fetchReturning(new Response("error", { status }));
    await expect(fetchRequest("fixit-test", "r1", "t", fetchFn as typeof fetch)).resolves.toEqual({
      ok: false,
      status: 502,
    });
  });

  test("network error or malformed document -> failure 502", async () => {
    const failing = vi.fn(async () => {
      throw new TypeError("network down");
    });
    await expect(fetchRequest("fixit-test", "r1", "t", failing as unknown as typeof fetch)).resolves.toEqual({
      ok: false,
      status: 502,
    });
    const { status: _s, ...noStatus } = acceptedFields;
    await expect(
      fetchRequest("fixit-test", "r1", "t", fetchReturning(Response.json(doc(noStatus))) as typeof fetch),
    ).resolves.toEqual({ ok: false, status: 502 });
    await expect(
      fetchRequest("fixit-test", "r1", "t", fetchReturning(new Response("not json")) as typeof fetch),
    ).resolves.toEqual({ ok: false, status: 502 });
  });

  test("an unparseable timestamp maps to undefined (the event is then treated as stale)", async () => {
    const fetchFn = fetchReturning(
      Response.json(doc({ ...acceptedFields, acceptedAt: { timestampValue: "yesterday" } })),
    );
    const result = await fetchRequest("fixit-test", "r1", "t", fetchFn as typeof fetch);
    expect(result.ok && result.request.acceptedAt).toBeUndefined();
  });
});

describe("parseTimestamp (RFC 3339, fraction truncated to ms)", () => {
  const expected = Date.parse("2026-10-08T12:34:56.123Z");

  test("without fraction", () => {
    expect(parseTimestamp("2026-10-08T12:34:56Z")).toBe(Date.parse("2026-10-08T12:34:56.000Z"));
  });

  test("with milliseconds", () => {
    expect(parseTimestamp("2026-10-08T12:34:56.123Z")).toBe(expected);
  });

  test("with microseconds", () => {
    expect(parseTimestamp("2026-10-08T12:34:56.123456Z")).toBe(expected);
  });

  test("with nanoseconds gives the same epoch ms as the millisecond form", () => {
    expect(parseTimestamp("2026-10-08T12:34:56.123456789Z")).toBe(expected);
    expect(parseTimestamp("2026-10-08T12:34:56.999999999Z")).toBe(Date.parse("2026-10-08T12:34:56.999Z"));
  });

  test("with one or two fraction digits", () => {
    expect(parseTimestamp("2026-10-08T12:34:56.1Z")).toBe(Date.parse("2026-10-08T12:34:56.100Z"));
    expect(parseTimestamp("2026-10-08T12:34:56.12Z")).toBe(Date.parse("2026-10-08T12:34:56.120Z"));
  });

  test("with a numeric offset", () => {
    expect(parseTimestamp("2026-10-08T09:34:56.123456-03:00")).toBe(expected);
  });

  test.each(["", "yesterday", "2026-10-08", "2026-10-08T12:34:56.1234567890Z", "2026-10-08T12:34:56"])(
    "rejects %j",
    (value) => {
      expect(parseTimestamp(value)).toBeUndefined();
    },
  );
});
