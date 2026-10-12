#!/usr/bin/env node
// Creates the FixIt test accounts in Firebase Auth and their `users/{uid}` profiles in Firestore, over the public REST
// APIs with each user's own ID token, so the writes go through the deployed security rules (design 13.3) exactly like
// the app's sign-up. Node 22 (built-in fetch), no dependencies.
//
// Usage (from any directory): node scripts/seed-test-users.mjs
//
// - Reads `api_key` and `project_id` from app/google-services.json (shared out of band, never committed).
// - Idempotent: an existing account is signed in instead of created (EMAIL_EXISTS) and an existing profile is kept.
// - The users mirror core/data/.../fake/SeedData.kt (same names, roles, phones, categories, base point and radius).
//   Reputation starts at 0 for everyone, as the create rule demands; no requests or reviews are seeded.

import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const PASSWORD = 'test1234';
const ANDROID_PACKAGE = 'com.vespelabs.fixit';
const root = join(dirname(fileURLToPath(import.meta.url)), '..');

/** Same people as SeedData.kt. Clients carry no professional fields, like User.toCreateFields() in Dtos.kt. */
const USERS = [
  { name: 'Ana López', email: 'ana@fixit.test', phone: '11 5555-0001', role: 'CLIENT' },
  { name: 'Bruno Díaz', email: 'bruno@fixit.test', phone: '11 5555-0002', role: 'CLIENT' },
  {
    name: 'Carlos Gómez', email: 'carlos@fixit.test', phone: '11 5555-0101', role: 'PROFESSIONAL',
    professional: { categories: ['ELECTRICITY', 'GAS'], baseLat: -34.8065, baseLng: -58.2788, baseAddress: 'Florencio Varela', radiusKm: 10 },
  },
  {
    name: 'Diana Ruiz', email: 'diana@fixit.test', phone: '11 5555-0102', role: 'PROFESSIONAL',
    professional: { categories: ['PLUMBING', 'GAS'], baseLat: -34.7206, baseLng: -58.2546, baseAddress: 'Quilmes', radiusKm: 8 },
  },
  {
    name: 'Esteban Paz', email: 'esteban@fixit.test', phone: '11 5555-0103', role: 'PROFESSIONAL',
    professional: { categories: ['CARPENTRY', 'PAINTING', 'OTHER'], baseLat: -34.76, baseLng: -58.212, baseAddress: 'Berazategui', radiusKm: 15 },
  },
];

function fail(message) {
  console.error(`seed-test-users: ${message}`);
  process.exit(1);
}

function readFirebaseConfig() {
  let config;
  try {
    config = JSON.parse(readFileSync(join(root, 'app', 'google-services.json'), 'utf8'));
  } catch (e) {
    fail(`cannot read app/google-services.json (${e.message})`);
  }
  const projectId = config.project_info?.project_id;
  const clients = config.client ?? [];
  const client = clients.find((c) => c.client_info?.android_client_info?.package_name === ANDROID_PACKAGE) ?? clients[0];
  const apiKey = client?.api_key?.[0]?.current_key;
  if (!projectId || !apiKey) fail('project_id or api_key missing in app/google-services.json');
  return { projectId, apiKey };
}

async function postJson(url, body, headers = {}) {
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify(body),
  });
  const json = await response.json().catch(() => ({}));
  return { ok: response.ok, status: response.status, json };
}

/** Auth REST: sign up, or sign in when the email already exists. Returns { uid, idToken, created }. */
async function authenticate(apiKey, email) {
  const base = 'https://identitytoolkit.googleapis.com/v1/accounts';
  const credentials = { email, password: PASSWORD, returnSecureToken: true };
  const signUp = await postJson(`${base}:signUp?key=${apiKey}`, credentials);
  if (signUp.ok) return { uid: signUp.json.localId, idToken: signUp.json.idToken, created: true };
  const reason = signUp.json.error?.message ?? `HTTP ${signUp.status}`;
  if (!reason.startsWith('EMAIL_EXISTS')) throw new Error(`signUp ${email}: ${reason}`);
  const signIn = await postJson(`${base}:signInWithPassword?key=${apiKey}`, credentials);
  if (!signIn.ok) throw new Error(`signIn ${email}: ${signIn.json.error?.message ?? `HTTP ${signIn.status}`}`);
  return { uid: signIn.json.localId, idToken: signIn.json.idToken, created: false };
}

/**
 * Firestore REST fields with the same names and types as User.toCreateFields() (Dtos.kt): strings, an array of
 * category names, doubles for the base point, integers for radiusKm, ratingSum = 0 and reviewCount = 0.
 * createdAt is NOT here: it is set by a REQUEST_TIME transform (the REST twin of FieldValue.serverTimestamp()).
 */
function profileFields(user) {
  const fields = {
    name: { stringValue: user.name },
    email: { stringValue: user.email },
    phone: { stringValue: user.phone },
    role: { stringValue: user.role },
  };
  if (user.professional) {
    const p = user.professional;
    fields.categories = { arrayValue: { values: p.categories.map((c) => ({ stringValue: c })) } };
    fields.baseLat = { doubleValue: p.baseLat };
    fields.baseLng = { doubleValue: p.baseLng };
    fields.baseAddress = { stringValue: p.baseAddress };
    fields.radiusKm = { integerValue: String(p.radiusKm) };
  }
  fields.ratingSum = { integerValue: '0' };
  fields.reviewCount = { integerValue: '0' };
  return fields;
}

/** Writes users/{uid} unless it exists. Returns 'written' or 'kept'. */
async function writeProfile(projectId, auth, user) {
  const database = `projects/${projectId}/databases/(default)`;
  const documentName = `${database}/documents/users/${auth.uid}`;
  const headers = { Authorization: `Bearer ${auth.idToken}` };

  const existing = await fetch(`https://firestore.googleapis.com/v1/${documentName}`, { headers });
  if (existing.ok) return 'kept';
  if (existing.status !== 404) {
    const body = await existing.json().catch(() => ({}));
    throw new Error(`read users/${auth.uid}: ${body.error?.message ?? `HTTP ${existing.status}`}`);
  }

  const commit = await postJson(`https://firestore.googleapis.com/v1/${database}/documents:commit`, {
    writes: [{
      update: { name: documentName, fields: profileFields(user) },
      updateTransforms: [{ fieldPath: 'createdAt', setToServerValue: 'REQUEST_TIME' }],
      currentDocument: { exists: false }, // never overwrites a profile created in between
    }],
  }, headers);
  if (!commit.ok) throw new Error(`write users/${auth.uid}: ${commit.json.error?.message ?? `HTTP ${commit.status}`}`);
  return 'written';
}

async function main() {
  const { projectId, apiKey } = readFirebaseConfig();
  console.log(`Seeding test users in project ${projectId} (password: ${PASSWORD})`);
  const rows = [];
  let failures = 0;
  for (const user of USERS) {
    try {
      const auth = await authenticate(apiKey, user.email);
      const profile = await writeProfile(projectId, auth, user);
      rows.push({ email: user.email, role: user.role, uid: auth.uid, account: auth.created ? 'created' : 'existing', profile });
    } catch (e) {
      failures += 1;
      rows.push({ email: user.email, role: user.role, uid: '-', account: 'error', profile: e.message });
    }
  }
  console.table(rows);
  if (failures > 0) process.exit(1);
}

await main();
