# JobFinder: first Store submission, your clicks in Partner Center

Product 9NG3TW4878DR (JobFinderApp). Texts are in `docs/store/listing.md` and
`docs/store/restricted-capabilities.md`.

Before you start:
- [ ] Push the site repo so https://raghavsatyadev.github.io/projects/jobfinder/privacy/ is live
  (file `projects/jobfinder/privacy/index.html` and `sitemap.xml`), and open the URL once.
- [ ] Upload file: `build/store-upload/JobFinder-0.0.60.0.msix` in the repo folder
  (unsigned; the Store signs it). Do not upload a test-signed copy.

In Partner Center → Apps and games → JobFinderApp → **Start your submission**:

1. **Pricing and availability**
   - [ ] Markets: all (or pick yours). Visibility: Public. Pricing: Free. Release: as soon as it
     passes certification (or "manually" if you want to press Publish yourself).
2. **Properties**
   - [ ] Category Business, no subcategory; secondary category Productivity.
   - [ ] Privacy policy: answer **Yes** to "accesses, collects or transmits personal information",
     paste the privacy URL.
   - [ ] Website and support contact (GitHub repo and its Issues page).
   - [ ] Product declarations: tick "incorporates generative AI features"; leave the rest at their
     defaults.
   - [ ] System requirements: optional (Windows 10 2004 or later, x64, 8 GB RAM recommended for
     the local AI model).
3. **Age ratings**
   - [ ] Start the IARC questionnaire, category Utility/Productivity, answers as in `listing.md`.
     Save and generate.
4. **Packages**
   - [ ] Drag in the `.msix`. Wait for validation; it should show Identity
     DreamWorldApps.JobFinderApp, version 0.0.60.0, x64, Windows 10 Desktop 10.0.19041+.
   - [ ] Device family availability: Windows 10/11 Desktop only.
5. **Store listings** → English (United States)
   - [ ] Description, short description, product features, keywords, copyright, from `listing.md`.
   - [ ] Screenshots: upload `docs/store/screenshots/01-…08-*.png` in order, add the captions.
   - [ ] Store logos: 1:1 app tile icon `docs/store/logos/app-tile-icon-300x300.png`.
6. **Submission options**
   - [ ] Restricted capabilities: paste the runFullTrust and unvirtualizedResources texts.
   - [ ] Notes for certification: "No account or sign-in is needed. The app works offline; job
     searches need a job board link (for example a LinkedIn jobs search URL) and optionally the
     user's own job board sign-in. The local AI model (Ollama) is downloaded on first run and is
     optional for reviewing the UI."
7. [ ] **Submit for certification.** Expect 1–3 business days. If certification rejects
   unvirtualizedResources, follow the fallback in `restricted-capabilities.md` and tell me.

After it is published:
- [ ] Send me the Store link; I'll add a "Get it from Microsoft" button to the JobFinder page and
  README.
- [ ] Your installed test build (test-signed, same identity) will not update from the Store until
  you remove it and install from the Store (`Get-AppxPackage DreamWorldApps.JobFinderApp |
  Remove-AppxPackage`, then install from the Store page). Your data folder stays.

WACK (Windows App Certification Kit, run 2026-11-27 on a test-signed copy of the same build):
**Overall PASS.** Only "Blocked executables" failed. That test is informational for Desktop Bridge
apps and S mode: the JVM/JCEF binaries call CreateProcess, and the app runs `reg.exe` and PowerShell
on purpose. No fix needed.
