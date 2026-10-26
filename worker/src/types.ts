// Wire values shared with the Android app (core/data/push): must match exactly.
export const EVENT_TYPES = ["NEW_REQUEST", "REQUEST_ACCEPTED", "REQUEST_CANCELLED"] as const;
export type PushEventType = (typeof EVENT_TYPES)[number];

export interface NotifyBody {
  type: PushEventType;
  requestId: string;
}

/** The `requests/{id}` document as re-read from Firestore; timestamps in epoch ms. */
export interface RequestDoc {
  id: string;
  status: string;
  clientId: string;
  professionalId?: string | undefined;
  category: string;
  createdAt?: number | undefined;
  acceptedAt?: number | undefined;
  cancelledAt?: number | undefined;
}

/** FCM data payload: exactly these keys, no personal data nor coordinates (D18). */
export type PushData =
  | { type: "NEW_REQUEST"; requestId: string; category: string }
  | { type: "REQUEST_ACCEPTED" | "REQUEST_CANCELLED"; requestId: string };

export type Decision = { topic: string; data: PushData } | { reject: 403 };
