import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: 'tests',
  timeout: 60_000,
  // One persistent browser profile per test; extensions do not share well across workers.
  workers: 1,
  reporter: 'list',
});
