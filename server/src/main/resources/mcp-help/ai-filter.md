# Write the AI filter

Job boards show jobs that don't fit. For each new job, the AI reads the title, company, place and description, and the user's filter description, and either keeps it in **New** or moves it to **Filtered out** with a reason.

Rules the app applies before the AI:
- A company on the skip list is filtered out at once (compared in lower case).
- A job whose description couldn't be read is filtered out ("No job description could be read."); the app tries to read it again later.

When the description or skip list changes, jobs already in New are checked again in the background. Jobs the user moved to New themselves are left alone.

## Writing the description (`set_ai_filter`)
- Plain sentences, at most 5,000 characters; 1,500 or less works best on the local model.
- Say what to filter **out**, concretely: roles and seniority, skills or stacks that aren't the user's main one, lowest salary with currency, work modes and places, domains or company types.
- Say what to keep when it is subtle: "Keep Android roles that also mention Kotlin Multiplatform."
- Don't describe an answer format; the app sets it.

Example:
> Filter out jobs that are not mainly Android development (web, backend, iOS-only, QA, Flutter-only). Filter out junior and intern roles, and roles needing under 5 years. Filter out on-site roles outside Bengaluru; keep remote roles open to India. Filter out jobs paying under ₹40 LPA when a salary is shown. Filter out gambling and crypto companies.

## Check it
`try_ai_prompt(id="matching", text=...)` runs the filter's instructions on the newest job in New without saving. The user can also press "Shouldn't be in New" on a job: the AI checks it again with the user's note and moves it when the user is right.
