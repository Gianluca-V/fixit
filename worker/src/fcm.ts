import type { PushData } from "./types";

export interface FcmMessage {
  message: {
    topic: string;
    data: PushData;
    android: { priority: "NORMAL" | "HIGH"; ttl: string };
  };
}

/**
 * Data-only message (no `notification` block): the device builds the visible text
 * from its own strings. A new-request notice is mostly discarded by distance on the
 * device and is pointless after 1 h, so it goes NORMAL with a short TTL; HIGH with
 * no visible notice could be deprioritised by Android. Accept/cancel are personal
 * and matter for a day.
 */
export function buildFcmMessage(topic: string, data: PushData): FcmMessage {
  const android =
    data.type === "NEW_REQUEST"
      ? ({ priority: "NORMAL", ttl: "3600s" } as const)
      : ({ priority: "HIGH", ttl: "86400s" } as const);
  return { message: { topic, data, android } };
}

/** Resolves once FCM accepts the message; delivery itself is never awaited. */
export async function sendFcm(
  projectId: string,
  accessToken: string,
  message: FcmMessage,
  fetchFn: typeof fetch = fetch,
): Promise<void> {
  const res = await fetchFn(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
    method: "POST",
    headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" },
    body: JSON.stringify(message),
  });
  if (!res.ok) throw new Error(`FCM send failed: ${res.status}`);
}
