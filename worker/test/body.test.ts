import { describe, expect, test } from "vitest";
import { parseNotifyBody } from "../src/body";

describe("parseNotifyBody", () => {
  test.each(["NEW_REQUEST", "REQUEST_ACCEPTED", "REQUEST_CANCELLED"])("accepts type %s", (type) => {
    expect(parseNotifyBody({ type, requestId: "r1" })).toEqual({ type, requestId: "r1" });
  });

  test("accepts a 40-character alphanumeric requestId", () => {
    const requestId = "aZ09".repeat(10);
    expect(parseNotifyBody({ type: "NEW_REQUEST", requestId })).toEqual({ type: "NEW_REQUEST", requestId });
  });

  test("rejects an unknown type", () => {
    expect(parseNotifyBody({ type: "REQUEST_COMPLETED", requestId: "r1" })).toBeNull();
    expect(parseNotifyBody({ type: "new_request", requestId: "r1" })).toBeNull();
    expect(parseNotifyBody({ requestId: "r1" })).toBeNull();
  });

  test.each(["", "a/b", "..", "../users/x", "r 1", "r-1", "a".repeat(41)])("rejects requestId %j", (requestId) => {
    expect(parseNotifyBody({ type: "NEW_REQUEST", requestId })).toBeNull();
  });

  test("rejects a non-string requestId", () => {
    expect(parseNotifyBody({ type: "NEW_REQUEST", requestId: 12 })).toBeNull();
    expect(parseNotifyBody({ type: "NEW_REQUEST" })).toBeNull();
  });

  test.each([null, undefined, "NEW_REQUEST", 42, true, [], ["NEW_REQUEST", "r1"]])("rejects non-object body %j", (json) => {
    expect(parseNotifyBody(json)).toBeNull();
  });

  test("ignores any other field: a topic or category in the body never reaches the result", () => {
    const parsed = parseNotifyBody({
      type: "NEW_REQUEST",
      requestId: "r1",
      topic: "category_ELECTRICITY",
      category: "ELECTRICITY",
      clientId: "attacker",
    });
    expect(parsed).toEqual({ type: "NEW_REQUEST", requestId: "r1" });
    expect(Object.keys(parsed ?? {})).toEqual(["type", "requestId"]);
  });
});
