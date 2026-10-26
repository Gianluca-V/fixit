import { EVENT_TYPES, type NotifyBody, type PushEventType } from "./types";

// Alphanumeric only: the id is interpolated into the Firestore document URL, so
// `/`, `..` or `%` must never reach it.
const REQUEST_ID = /^[A-Za-z0-9]{1,40}$/;

/**
 * Reads only `type` and `requestId`; every other field (topic, category, ids) is
 * dropped because the target always comes from the re-read Firestore document.
 */
export function parseNotifyBody(json: unknown): NotifyBody | null {
  if (typeof json !== "object" || json === null || Array.isArray(json)) return null;
  const { type, requestId } = json as Record<string, unknown>;
  if (typeof type !== "string" || !(EVENT_TYPES as readonly string[]).includes(type)) return null;
  if (typeof requestId !== "string" || !REQUEST_ID.test(requestId)) return null;
  return { type: type as PushEventType, requestId };
}
