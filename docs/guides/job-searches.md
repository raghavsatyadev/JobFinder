# Job searches

A search is the address of a **search results page** on a job board, with your filters already
applied. Each time JobFinder searches, it opens the page, reads the job list and keeps the jobs it
has not seen yet.

## Add a search

1. Search on the job site as you normally do, with its filters (role, place, remote, seniority).
2. Sort by newest, and limit it to recent jobs (last 24 hours or last week).
3. Copy the address of the results page and paste it in **Searches**. JobFinder checks that the
   page shows a job list and tells you how many jobs it found.

Not sure which pages to add? The setup can copy a request for an AI assistant you already use
(Claude, Gemini, ChatGPT). It suggests search pages for your skills; paste its answer back to add
them all. An [AI agent](ai-agents.md) can add them for you too.

## Good searches

- One page per role and place: "Senior Android, Remote" and "Senior Android, Bengaluru" are two
  searches.
- 10 to 15 searches across the big boards and the boards of your field and country. More searches
  take longer, and some boards block too many requests.
- Name them `Board: Role, Place`, for example `LinkedIn: Senior Android, Remote`.

Example LinkedIn address: remote Senior Android jobs in India, last 24 hours, newest first:

```
https://www.linkedin.com/jobs/search/?keywords=Senior%20Android%20Developer&location=India&f_TPR=r86400&f_WT=2&sortBy=DD
```

`f_TPR=r86400` is the last 24 hours (`r604800` the last week); `f_WT` is 1 on-site, 2 remote,
3 hybrid; `sortBy=DD` is newest first.

## Boards

LinkedIn, Indeed, Glassdoor, Naukri, Dice, ZipRecruiter, Wellfound, Built In, Hiring Cafe,
Remote OK, Remotive, We Work Remotely, USA Jobs and more have built-in readers. Other boards work
too: the AI reads their pages, which is slower and uses AI quota.

## A search stopped working

A search that fails three times in a row is marked as broken. Usually the sign-in expired (sign in
again from Setup → Open sign-in), the search shows no jobs any more, or the board changed its
addresses (search again and paste the new address).
