import { describe, expect, test } from "vitest";
import { decideTarget, EVENT_MAX_AGE_MS, EVENT_MAX_SKEW_MS } from "../src/rules";
import type { RequestDoc } from "../src/types";

const NOW = Date.parse("2026-10-08T12:00:00.000Z");
const RECENT = NOW - 5_000;
const open: RequestDoc = { id: "r1", status: "OPEN", clientId: "c1", category: "GAS", createdAt: RECENT };
const accepted: RequestDoc = { id: "r1", status: "ACCEPTED", clientId: "c1", professionalId: "p1", category: "GAS", createdAt: RECENT, acceptedAt: RECENT };
const cancelled: RequestDoc = {
  id: "r1",
  status: "CANCELLED",
  clientId: "c1",
  professionalId: "p1",
  category: "GAS",
  createdAt: RECENT,
  cancelledAt: RECENT,
};
const REJECT = { reject: 403 };

describe("decideTarget: state and caller rules", () => {
  test("NEW_REQUEST payload for GAS and r1 is exactly { type, requestId, category }", () => {
    const decision = decideTarget("NEW_REQUEST", open, "c1", NOW);
    expect(decision).toEqual({
      topic: "category_GAS",
      data: { type: "NEW_REQUEST", requestId: "r1", category: "GAS" },
    });
    if ("data" in decision) expect(Object.keys(decision.data).sort()).toEqual(["category", "requestId", "type"]);
  });

  test("REQUEST_ACCEPTED goes to user_{clientId} with only type and requestId", () => {
    const decision = decideTarget("REQUEST_ACCEPTED", accepted, "p1", NOW);
    expect(decision).toEqual({ topic: "user_c1", data: { type: "REQUEST_ACCEPTED", requestId: "r1" } });
    if ("data" in decision) expect(Object.keys(decision.data).sort()).toEqual(["requestId", "type"]);
  });

  test("REQUEST_CANCELLED goes to user_{professionalId} with only type and requestId", () => {
    const decision = decideTarget("REQUEST_CANCELLED", cancelled, "c1", NOW);
    expect(decision).toEqual({ topic: "user_p1", data: { type: "REQUEST_CANCELLED", requestId: "r1" } });
    if ("data" in decision) expect(Object.keys(decision.data).sort()).toEqual(["requestId", "type"]);
  });

  test("NEW_REQUEST requires OPEN and the owner as caller", () => {
    expect(decideTarget("NEW_REQUEST", { ...open, status: "ACCEPTED" }, "c1", NOW)).toEqual(REJECT);
    expect(decideTarget("NEW_REQUEST", open, "someoneElse", NOW)).toEqual(REJECT);
  });

  test("REQUEST_ACCEPTED requires ACCEPTED and the assigned professional as caller", () => {
    expect(decideTarget("REQUEST_ACCEPTED", { ...accepted, status: "OPEN" }, "p1", NOW)).toEqual(REJECT);
    expect(decideTarget("REQUEST_ACCEPTED", accepted, "p2", NOW)).toEqual(REJECT);
    expect(decideTarget("REQUEST_ACCEPTED", accepted, "c1", NOW)).toEqual(REJECT);
    expect(decideTarget("REQUEST_ACCEPTED", { ...accepted, professionalId: undefined }, "p1", NOW)).toEqual(REJECT);
  });

  test("REQUEST_CANCELLED requires CANCELLED, the owner as caller and an assigned professional", () => {
    expect(decideTarget("REQUEST_CANCELLED", { ...cancelled, status: "ACCEPTED" }, "c1", NOW)).toEqual(REJECT);
    expect(decideTarget("REQUEST_CANCELLED", cancelled, "p1", NOW)).toEqual(REJECT);
    expect(decideTarget("REQUEST_CANCELLED", { ...cancelled, professionalId: undefined }, "c1", NOW)).toEqual(REJECT);
  });

  test("topic and category always come from the re-read request", () => {
    expect(decideTarget("NEW_REQUEST", { ...open, category: "ELECTRICITY" }, "c1", NOW)).toEqual({
      topic: "category_ELECTRICITY",
      data: { type: "NEW_REQUEST", requestId: "r1", category: "ELECTRICITY" },
    });
    expect(decideTarget("REQUEST_ACCEPTED", { ...accepted, clientId: "c9" }, "p1", NOW)).toMatchObject({ topic: "user_c9" });
  });
});

describe("decideTarget: event freshness (replay protection)", () => {
  const cases = [
    { type: "NEW_REQUEST", request: open, uid: "c1", mark: "createdAt" },
    { type: "REQUEST_ACCEPTED", request: accepted, uid: "p1", mark: "acceptedAt" },
    { type: "REQUEST_CANCELLED", request: cancelled, uid: "c1", mark: "cancelledAt" },
  ] as const;

  test("the window constants are 2 min of age and 60 s of clock skew", () => {
    expect(EVENT_MAX_AGE_MS).toBe(120_000);
    expect(EVENT_MAX_SKEW_MS).toBe(60_000);
  });

  describe.each(cases)("$type uses $mark", ({ type, request, uid, mark }) => {
    const at = (ms: number | undefined): RequestDoc => ({ ...request, [mark]: ms });

    test("older than 2 min -> 403", () => {
      expect(decideTarget(type, at(NOW - 120_001), uid, NOW)).toEqual(REJECT);
      expect(decideTarget(type, at(NOW - 10 * 60_000), uid, NOW)).toEqual(REJECT);
    });

    test("missing mark -> 403", () => {
      expect(decideTarget(type, at(undefined), uid, NOW)).toEqual(REJECT);
    });

    test("exactly 120 s old -> accepted", () => {
      expect(decideTarget(type, at(NOW - 120_000), uid, NOW)).toHaveProperty("topic");
    });

    test("recent -> accepted", () => {
      expect(decideTarget(type, at(NOW - 1_000), uid, NOW)).toHaveProperty("topic");
      expect(decideTarget(type, at(NOW), uid, NOW)).toHaveProperty("topic");
    });

    test("more than 60 s in the future -> 403", () => {
      expect(decideTarget(type, at(NOW + 60_001), uid, NOW)).toEqual(REJECT);
    });

    test("exactly 60 s in the future (tolerated skew) -> accepted", () => {
      expect(decideTarget(type, at(NOW + 60_000), uid, NOW)).toHaveProperty("topic");
    });
  });

  test("only the mark of the event type counts", () => {
    const staleCreation = { ...accepted, createdAt: NOW - 3_600_000 };
    expect(decideTarget("REQUEST_ACCEPTED", staleCreation, "p1", NOW)).toHaveProperty("topic");
    expect(decideTarget("REQUEST_ACCEPTED", { ...accepted, acceptedAt: undefined, createdAt: RECENT }, "p1", NOW)).toEqual(REJECT);
  });
});
