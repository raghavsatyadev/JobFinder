// Bundles the extension into dist/ (the sideload build the app ships). `--watch` rebuilds on change.
// `--e2e` builds the Playwright copy into dist-e2e/: host permission for 127.0.0.1 and a fake app
// answering form requests. `--store` builds the Chrome Web Store / Edge Add-ons copy into dist-store/:
// no sign-in sharing (no `cookies` permission, no cookie code).
import * as esbuild from 'esbuild';
import { cpSync, readFileSync, rmSync, writeFileSync } from 'node:fs';

const watch = process.argv.includes('--watch');
const e2e = process.argv.includes('--e2e');
const store = process.argv.includes('--store');
if (e2e && store) throw new Error('build.mjs: use --e2e or --store, not both.');
const outdir = e2e ? 'dist-e2e' : store ? 'dist-store' : 'dist';
/** The local test page's origin; the store build never has it. */
const E2E_HOSTS = ['*://127.0.0.1/*', '*://*.127.0.0.1/*'];
const { version } = JSON.parse(readFileSync('package.json', 'utf8'));

// Static files are copied after every build so watch mode picks up HTML/CSS edits too.
const copyStatic = {
  name: 'copy-static',
  setup(build) {
    build.onEnd(() => {
      cpSync('static', outdir, { recursive: true });
      const manifest = JSON.parse(readFileSync('static/manifest.json', 'utf8'));
      manifest.version = version;
      if (e2e) manifest.host_permissions = E2E_HOSTS;
      if (store) {
        const optional = (manifest.optional_permissions ?? []).filter((p) => p !== 'cookies');
        if (optional.length) manifest.optional_permissions = optional;
        else delete manifest.optional_permissions;
      }
      writeFileSync(`${outdir}/manifest.json`, JSON.stringify(manifest, null, 2));
      if (store) {
        const options = readFileSync('static/options.html', 'utf8');
        const stripped = options.replace(/\s*<section id="signins-section"[\s\S]*?<\/section>/, '');
        if (stripped === options) throw new Error('build.mjs: options.html has no signins-section to remove.');
        writeFileSync(`${outdir}/options.html`, stripped);
      }
    });
  },
};

rmSync(outdir, { recursive: true, force: true });

const options = {
  entryPoints: {
    background: 'src/background/index.ts',
    popup: 'src/popup/popup.ts',
    options: 'src/options/options.ts',
    content: 'src/content/content.ts',
  },
  outdir,
  define: { __E2E__: String(e2e), __SIGNINS__: String(!store) },
  bundle: true,
  format: 'iife',
  target: 'chrome116',
  sourcemap: watch ? 'inline' : false,
  logLevel: 'info',
  plugins: [copyStatic],
};

if (watch) {
  await (await esbuild.context(options)).watch();
} else {
  await esbuild.build(options);
}
