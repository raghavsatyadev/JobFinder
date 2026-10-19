# Craft job searches

A job search is the full address of a **search results page** on a job board, with the filters already applied. On each scan JobFinder opens it, reads the job list and stores jobs it hasn't seen.

## Good searches
- One page per role and place: "Senior Android, Remote" and "Senior Android, Bengaluru" are two searches.
- Newest first, and limited to recent jobs (last 24 hours or last week), so each scan sees what's new.
- The user's seniority level where the board has that filter.
- 10 to 15 searches across the big boards and the niche boards of the user's field and country. More searches mean longer scans and more risk of a board blocking the address.
- Titles like `Board: Role, Place`, for example `LinkedIn: Senior Android, Remote`.

## Building the address
Use the board's own search with its filters, then take the results page address. Examples:
- **LinkedIn:** `https://www.linkedin.com/jobs/search/?keywords=Senior%20Android%20Developer&location=India&f_TPR=r86400&f_WT=2&sortBy=DD`
  - `f_TPR=r86400` last 24 hours (`r604800` last week); `f_WT` 1 on-site, 2 remote, 3 hybrid; `sortBy=DD` newest first; `f_E` experience (2 entry, 3 associate, 4 mid-senior, 5 director).
- **Indeed:** `https://in.indeed.com/jobs?q=android+developer&l=Bengaluru&fromage=1&sort=date` (`fromage` = days).
- **Naukri, Glassdoor, Wellfound, others:** run the search on the site and copy the results page address.

`list_job_boards` gives the boards the app reads with built-in readers. Other boards work too: the app's AI reads their pages (slower, and it uses AI quota).

## Adding them
`add_job_search(title, url)` opens the page and checks it shows a job list; it reports how many jobs it found. When no list is recognised it is not saved: check the address is a results page, not a single job, a home page or a sign-in page. Use `force=true` only when the user agrees (for a board the app will read with AI later).

The page opens signed in to the board only after the user signed in through the app (Setup → Open sign-in). LinkedIn shows fewer jobs to signed-out visitors.

## Broken searches
`list_job_searches` shows `failedScansInARow`; 3 or more is broken. Usually the board changed its address format, the search shows no jobs any more, or the sign-in expired. Fix the address with `update_job_search`, or ask the user to sign in again.
