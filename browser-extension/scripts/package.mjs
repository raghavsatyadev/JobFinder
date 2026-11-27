// Zips dist-store/ for the Chrome Web Store and Edge Add-ons: dist-zip/jobfinder-helper-<version>.zip.
// Run `npm run build:store` first (`npm run package` does both). The store build has no sign-in
// sharing: no `cookies` permission and no cookie code. The manifest `key` is dropped, because
// the Chrome Web Store rejects a package that has one.
//
// `--first-upload` also writes jobfinder-helper-<version>-first-upload.zip with dev-key.pem as
// key.pem at the root. Upload that one only once, as the first package of a new store item, so
// the store keeps the id cnleajglbkmfgfkfihheoacmojpklpop. It holds the private key: never share
// it or upload it anywhere else. Later uploads must not contain a .pem.
import { createHash, createPrivateKey, createPublicKey } from 'node:crypto';
import { existsSync, mkdirSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { crc32, deflateRawSync } from 'node:zlib';

const DIST = 'dist-store';
const OUT = 'dist-zip';
const DEV_ID = 'cnleajglbkmfgfkfihheoacmojpklpop';
const firstUpload = process.argv.includes('--first-upload');

function fail(message) {
  console.error(`package: ${message}`);
  process.exit(1);
}

if (!existsSync(join(DIST, 'manifest.json'))) fail('dist-store/manifest.json is missing: run `npm run build:store` first.');

/** Every file under dir, as zip paths with forward slashes, sorted for a stable zip. */
function listFiles(dir) {
  const out = [];
  const walk = (d) => {
    for (const name of readdirSync(d)) {
      const path = join(d, name);
      if (statSync(path).isDirectory()) walk(path);
      else out.push(relative(dir, path).split(sep).join('/'));
    }
  };
  walk(dir);
  return out.sort();
}

const manifest = JSON.parse(readFileSync(join(DIST, 'manifest.json'), 'utf8'));
const version = manifest.version;
if (!/^(0|[1-9]\d{0,4})(\.(0|[1-9]\d{0,4})){0,3}$/.test(version) || version.split('.').some((n) => +n > 65535)) {
  fail(`version "${version}" is not 1 to 4 dot-separated numbers from 0 to 65535.`);
}
if (/^[0.]+$/.test(version)) fail('version must not be all zeros.');
// The store build asks for host access only at run time; a fixed host list means an e2e build.
if (manifest.host_permissions) fail('dist-store/ has host_permissions: that is an e2e build. Run `npm run build:store`.');
if (JSON.stringify(manifest).includes('"cookies"')) fail('the manifest asks for cookies: run `npm run build:store`.');
delete manifest.key;

const files = listFiles(DIST).filter((f) => f !== 'manifest.json');
const bad = files.filter((f) => /(^|\/)\.|\.pem$|\.map$|\.ts$/.test(f));
if (bad.length) fail(`dist-store/ has files that must not ship: ${bad.join(', ')}`);
const background = readFileSync(join(DIST, 'background.js'), 'utf8');
if (background.includes('function fakeHostReply')) fail('background.js has the e2e fake app: run `npm run build:store`.');
const cookieCode = files.filter((f) => f.endsWith('.js') && /chrome\.cookies|cookies\.getAll|\["cookies"\]/.test(readFileSync(join(DIST, f), 'utf8')));
if (cookieCode.length) fail(`${cookieCode.join(', ')} still has sign-in sharing code: run \`npm run build:store\`.`);

/** A minimal zip (deflate, no zip64): enough for a few hundred kB of extension files. */
function zip(entries) {
  const locals = [];
  const centrals = [];
  let offset = 0;
  // 1980-01-01 00:00: a fixed time keeps the zip byte-for-byte reproducible.
  const time = 0;
  const date = (0 << 9) | (1 << 5) | 1;
  for (const { name, data } of entries) {
    const nameBuf = Buffer.from(name, 'utf8');
    const packed = deflateRawSync(data, { level: 9 });
    const crc = crc32(data);
    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0);
    local.writeUInt16LE(20, 4);
    local.writeUInt16LE(0x0800, 6); // UTF-8 names
    local.writeUInt16LE(8, 8); // deflate
    local.writeUInt16LE(time, 10);
    local.writeUInt16LE(date, 12);
    local.writeUInt32LE(crc, 14);
    local.writeUInt32LE(packed.length, 18);
    local.writeUInt32LE(data.length, 22);
    local.writeUInt16LE(nameBuf.length, 26);
    locals.push(local, nameBuf, packed);
    const central = Buffer.alloc(46);
    central.writeUInt32LE(0x02014b50, 0);
    central.writeUInt16LE(20, 4);
    central.writeUInt16LE(20, 6);
    central.writeUInt16LE(0x0800, 8);
    central.writeUInt16LE(8, 10);
    central.writeUInt16LE(time, 12);
    central.writeUInt16LE(date, 14);
    central.writeUInt32LE(crc, 16);
    central.writeUInt32LE(packed.length, 20);
    central.writeUInt32LE(data.length, 24);
    central.writeUInt16LE(nameBuf.length, 28);
    central.writeUInt32LE(offset, 42);
    centrals.push(central, nameBuf);
    offset += local.length + nameBuf.length + packed.length;
  }
  const dir = Buffer.concat(centrals);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(entries.length, 8);
  end.writeUInt16LE(entries.length, 10);
  end.writeUInt32LE(dir.length, 12);
  end.writeUInt32LE(offset, 16);
  return Buffer.concat([...locals, dir, end]);
}

const entries = [
  { name: 'manifest.json', data: Buffer.from(JSON.stringify(manifest, null, 2)) },
  ...files.map((name) => ({ name, data: readFileSync(join(DIST, name)) })),
];
mkdirSync(OUT, { recursive: true });
const storeZip = join(OUT, `jobfinder-helper-${version}.zip`);
writeFileSync(storeZip, zip(entries));
console.log(`${storeZip}  (${entries.length} files, version ${version})`);

if (firstUpload) {
  const keyPath = 'dev-key.pem';
  if (!existsSync(keyPath)) fail('dev-key.pem is missing: restore it from your backup first.');
  const pem = readFileSync(keyPath);
  const der = createPublicKey(createPrivateKey(pem)).export({ type: 'spki', format: 'der' });
  const id = [...createHash('sha256').update(der).digest('hex').slice(0, 32)]
    .map((c) => String.fromCharCode(97 + parseInt(c, 16)))
    .join('');
  if (id !== DEV_ID) fail(`dev-key.pem gives id ${id}, not ${DEV_ID}.`);
  const firstZip = join(OUT, `jobfinder-helper-${version}-first-upload.zip`);
  writeFileSync(firstZip, zip([...entries, { name: 'key.pem', data: pem }]));
  console.log(`${firstZip}  (adds key.pem for id ${id}; first Chrome Web Store upload only)`);
}
