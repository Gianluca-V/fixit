#!/usr/bin/env node
// Writes firestore.deploy.rules (git-ignored) from firestore.rules, replacing the <CLOUD_NAME> placeholder with
// CLOUDINARY_CLOUD_NAME from local.properties (design 13.3, 13.7). firebase.json deploys the generated file, so the
// real cloud name never lands in the repository. Node 22, no dependencies.
//
// Usage (from any directory): node scripts/prepare-firestore-rules.mjs

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const PLACEHOLDER = '<CLOUD_NAME>';
const root = join(dirname(fileURLToPath(import.meta.url)), '..');

function fail(message) {
  console.error(`prepare-firestore-rules: ${message}`);
  process.exit(1);
}

/** Minimal Java .properties reader: `key=value` or `key: value`, `#`/`!` comments, surrounding spaces trimmed. */
function readProperties(path) {
  let text;
  try {
    text = readFileSync(path, 'utf8');
  } catch {
    fail(`cannot read ${path}`);
  }
  const properties = {};
  for (const rawLine of text.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (line === '' || line.startsWith('#') || line.startsWith('!')) continue;
    const match = line.match(/^([^=:\s]+)\s*[=:]\s*(.*)$/);
    if (match) properties[match[1]] = match[2].trim();
  }
  return properties;
}

const cloudName = readProperties(join(root, 'local.properties')).CLOUDINARY_CLOUD_NAME ?? '';
if (cloudName === '') fail('CLOUDINARY_CLOUD_NAME is missing or empty in local.properties');
// Cloud names are letters, digits, '-' and '_': nothing that could alter the regex in isPhotoUrl.
if (!/^[A-Za-z0-9][A-Za-z0-9_-]*$/.test(cloudName)) fail(`unexpected characters in CLOUDINARY_CLOUD_NAME: ${cloudName}`);

const source = readFileSync(join(root, 'firestore.rules'), 'utf8');
if (!source.includes(PLACEHOLDER)) fail(`${PLACEHOLDER} not found in firestore.rules`);

const output = source.replaceAll(PLACEHOLDER, cloudName);
writeFileSync(join(root, 'firestore.deploy.rules'), output, 'utf8');

const count = source.split(PLACEHOLDER).length - 1;
console.log(`firestore.deploy.rules written (${count} x ${PLACEHOLDER} -> ${cloudName}).`);
console.log('Deploy with: firebase deploy --only firestore --project <project-id>');
