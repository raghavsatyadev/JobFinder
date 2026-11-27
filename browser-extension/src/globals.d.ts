/** True only in the Playwright build (`node build.mjs --e2e`), which answers form requests itself. */
declare const __E2E__: boolean;
/** False in the store build (`node build.mjs --store`): sign-in sharing and the cookies permission are left out. */
declare const __SIGNINS__: boolean;
