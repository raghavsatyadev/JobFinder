// Bundles the extension into dist/. `--watch` rebuilds on change. `--e2e` builds the Playwright copy
// into dist-e2e/: host permission for 127.0.0.1 and a fake app answering form requests.
import * as esbuild from 'esbuild';
import { cpSync, readFileSync, rmSync, writeFileSync } from 'node:fs';

const watch = process.argv.includes('--watch');
const e2e = process.argv.includes('--e2e');
const outdir = e2e ? 'dist-e2e' : 'dist';
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
      writeFileSync(`${outdir}/manifest.json`, JSON.stringify(manifest, null, 2));
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
  define: { __E2E__: String(e2e) },
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
