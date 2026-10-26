import type { RequestDoc } from "./types";

export type FetchRequestResult = { ok: true; request: RequestDoc } | { ok: false; status: 404 | 502 };

type FirestoreValue = { stringValue?: unknown; timestampValue?: unknown; nullValue?: unknown };

// Firestore may return 0 to 9 fraction digits; the fraction is cut to milliseconds
// here instead of relying on how Date.parse treats more than 3 digits.
const RFC3339 = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?(Z|[+-]\d{2}:\d{2})$/;

export function parseTimestamp(value: string): number | undefined {
  const match = RFC3339.exec(value);
  if (!match) return undefined;
  const [, base, fraction = "", zone] = match;
  const ms = (fraction + "000").slice(0, 3);
  const epoch = Date.parse(`${base}.${ms}${zone}`);
  return Number.isNaN(epoch) ? undefined : epoch;
}

function str(fields: Record<string, FirestoreValue>, name: string): string | undefined {
  const v = fields[name]?.stringValue;
  return typeof v === "string" && v.length > 0 ? v : undefined;
}

function ts(fields: Record<string, FirestoreValue>, name: string): number | undefined {
  const v = fields[name]?.timestampValue;
  return typeof v === "string" ? parseTimestamp(v) : undefined;
}

/**
 * Re-reads `requests/{id}` with the caller's own ID token (D27): Firestore rules
 * apply and the service account needs no database role. The request body is never
 * trusted; `requestId` is already restricted to `[A-Za-z0-9]` by parseNotifyBody.
 */
export async function fetchRequest(
  projectId: string,
  requestId: string,
  idToken: string,
  fetchFn: typeof fetch = fetch,
): Promise<FetchRequestResult> {
  const url = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/requests/${requestId}`;
  let body: unknown;
  try {
    const res = await fetchFn(url, { method: "GET", headers: { Authorization: `Bearer ${idToken}` } });
    if (res.status === 404) return { ok: false, status: 404 };
    if (!res.ok) return { ok: false, status: 502 };
    body = await res.json();
  } catch {
    return { ok: false, status: 502 };
  }

  const fields = (body as { fields?: Record<string, FirestoreValue> } | null)?.fields;
  if (typeof fields !== "object" || fields === null) return { ok: false, status: 502 };
  const status = str(fields, "status");
  const clientId = str(fields, "clientId");
  const category = str(fields, "category");
  if (!status || !clientId || !category) return { ok: false, status: 502 };

  return {
    ok: true,
    request: {
      id: requestId,
      status,
      clientId,
      professionalId: str(fields, "professionalId"),
      category,
      createdAt: ts(fields, "createdAt"),
      acceptedAt: ts(fields, "acceptedAt"),
      cancelledAt: ts(fields, "cancelledAt"),
    },
  };
}
