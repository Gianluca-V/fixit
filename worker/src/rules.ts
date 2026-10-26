import type { Decision, PushEventType, RequestDoc } from "./types";

/** A valid ID token lasts 1 h; the event window keeps it from replaying old notices. */
export const EVENT_MAX_AGE_MS = 120_000;
/** Tolerated drift between the Firestore and Worker clocks for marks slightly "ahead". */
export const EVENT_MAX_SKEW_MS = 60_000;

const REJECT: Decision = { reject: 403 };

// Server marks only: Firestore rules pin them to request.time (§13.3), so a
// client cannot choose its own date; rejecting future ones is defence in depth.
function isFresh(mark: number | undefined, nowMs: number): boolean {
  if (mark === undefined || !Number.isFinite(mark)) return false;
  const age = nowMs - mark;
  return age >= -EVENT_MAX_SKEW_MS && age <= EVENT_MAX_AGE_MS;
}

/**
 * Pure event check of §14b.2. Topic and category are derived from the re-read
 * document only; the request body never contributes to the target.
 */
export function decideTarget(type: PushEventType, request: RequestDoc, uid: string, nowMs: number): Decision {
  const requestId = request.id;
  switch (type) {
    case "NEW_REQUEST":
      if (request.status !== "OPEN" || request.clientId !== uid) return REJECT;
      if (!isFresh(request.createdAt, nowMs)) return REJECT;
      return {
        topic: `category_${request.category}`,
        data: { type, requestId, category: request.category },
      };
    case "REQUEST_ACCEPTED":
      if (request.status !== "ACCEPTED" || !request.professionalId || request.professionalId !== uid) return REJECT;
      if (!isFresh(request.acceptedAt, nowMs)) return REJECT;
      return { topic: `user_${request.clientId}`, data: { type, requestId } };
    case "REQUEST_CANCELLED":
      if (request.status !== "CANCELLED" || request.clientId !== uid || !request.professionalId) return REJECT;
      if (!isFresh(request.cancelledAt, nowMs)) return REJECT;
      return { topic: `user_${request.professionalId}`, data: { type, requestId } };
  }
}
