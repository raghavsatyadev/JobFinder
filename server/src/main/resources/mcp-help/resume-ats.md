# Resume and ATS scores

The app reads the uploaded resume (PDF, DOCX or TXT) and flags problems ATS software trips on: tables, several columns, images, icon fonts, missing Experience/Skills/Education headings, no dates, no email or phone, too short or too long.

For each job it makes an ATS review: a keyword match (exact, a variant, or missing), experience relevance, achievement quality, education fit, trajectory, disqualifiers, an overall score, an assessment and fixes. Under a ready review the user can ask "Improve my resume for this job": exact before/after edits that keep every fact and number true; edits that add a skill or number the resume lacks are flagged (skills in the user's LinkedIn data are not).

Resume PDFs (Settings → Resume → Resume PDF, no AI): a one-column ATS-friendly PDF with the user's page size (A4/Letter), length (1–2 pages) and section order. "Save as PDF" in the improve dialog makes one for the job with the ticked edits, putting the job's words first and, when the user imported their LinkedIn data export ZIP, bringing in their own LinkedIn skills, role bullets, projects and certifications that have the job's missing words. To get the export: LinkedIn → Me → Settings & Privacy → Data privacy → Get a copy of your data → "Want something in particular?" (Profile, Positions, Education, Skills, Certifications, Projects, Languages, Honors, Courses) → Request archive; import the ZIP unzipped.

## Helping with the resume
- `get_resume` gives the text the app read and its problems.
- Rewrite rules: one column; standard headings (Summary, Experience, Skills, Education); company, title and dates for every role; short bullets that start with a verb and show results with numbers; the target roles' keywords only where true; no tables, images, icons or text boxes; one or two pages.
- Never invent experience, skills or numbers: ask the user.
- Save the new resume as a file and `upload_resume(path)`. Reviews are made again for it.
