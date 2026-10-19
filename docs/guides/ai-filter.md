# AI filter

Job boards show jobs that do not fit. For each new job the AI reads the title, company, place and
description and your filter, then keeps the job in **New** or moves it to **Filtered out** with the
reason. Edit the filter in **AI filters**.

Before the AI, two rules apply: a company on your skip list is filtered out at once, and a job
whose description could not be read waits in Filtered out until the app reads it again.

## Write the filter

- Plain sentences. Around 1,500 characters works best with the local model.
- Say what to filter **out**, concretely: roles and seniority, stacks that are not yours, the
  lowest salary with its currency, work modes and places, kinds of company.
- Say what to keep when it is subtle: "Keep Android roles that also mention Kotlin
  Multiplatform."

Example:

> Filter out jobs that are not mainly Android development (web, backend, iOS-only, QA,
> Flutter-only). Filter out junior and intern roles, and roles needing under 5 years. Filter out
> on-site roles outside Bengaluru; keep remote roles open to India. Filter out jobs paying under
> ₹40 LPA when a salary is shown. Filter out gambling and crypto companies.

## Check it

- **Try it on a job**, under the filter's AI instructions, runs your text on the newest job without
  saving it.
- On a job in New, **Shouldn't be in New** asks the AI to check it again with your note, and moves
  the job when you are right.
- When you change the filter or the skip list, the jobs already in New are checked again. Jobs you
  moved to New yourself stay.

The text the app gives the AI for each kind of work (filter, ATS review, tips, resume edits) can be
changed too, under **AI instructions**, with a reset to the default.
