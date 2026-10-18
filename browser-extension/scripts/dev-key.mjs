// Prints the manifest `key` and extension id for dev-key.pem, creating the key pair if missing.
// The private key stays local (gitignored); only the public key goes into static/manifest.json.
import { createHash, createPrivateKey, createPublicKey, generateKeyPairSync } from 'node:crypto';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const keyPath = fileURLToPath(new URL('../dev-key.pem', import.meta.url));

if (!existsSync(keyPath)) {
  const { privateKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
  writeFileSync(keyPath, privateKey.export({ type: 'pkcs8', format: 'pem' }));
  console.log(`Created ${keyPath}`);
}

const der = createPublicKey(createPrivateKey(readFileSync(keyPath))).export({ type: 'spki', format: 'der' });
// Chromium's id: first 16 bytes of SHA-256(public key DER), each hex digit 0-f mapped to a-p.
const id = [...createHash('sha256').update(der).digest('hex').slice(0, 32)]
  .map((c) => String.fromCharCode(97 + parseInt(c, 16)))
  .join('');

console.log(`manifest key: ${der.toString('base64')}`);
console.log(`extension id: ${id}`);
