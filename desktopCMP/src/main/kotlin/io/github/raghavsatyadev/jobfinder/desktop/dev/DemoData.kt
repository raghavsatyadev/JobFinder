package io.github.raghavsatyadev.jobfinder.desktop.dev

import io.github.raghavsatyadev.jobfinder.shared.model.AtsSummary
import io.github.raghavsatyadev.jobfinder.shared.model.Job
import io.github.raghavsatyadev.jobfinder.shared.model.JobStatus
import io.github.raghavsatyadev.jobfinder.shared.model.ResumeEdit
import io.github.raghavsatyadev.jobfinder.shared.model.ResumeImprovement
import io.github.raghavsatyadev.jobfinder.shared.model.ResumeSection
import io.github.raghavsatyadev.jobfinder.shared.model.WorkMode
import io.github.raghavsatyadev.jobfinder.ui.fake.FakeBackend
import io.github.raghavsatyadev.jobfinder.ui.fake.SampleData

/** The sample jobs with more new ones, so the tour's lists look lived in. */
object DemoData {
  private const val HOUR = 3_600_000L

  private val extra =
    listOf(
      Extra("Android Engineer II", "Adventure Works", "Remote, India", "₹28L – ₹36L", 74, 6.9),
      Extra("Lead Mobile Engineer (Kotlin)", "Proseware", "Bengaluru", null, 69, 6.4),
      Extra("Senior Kotlin Developer", "Woodgrove Bank", "Remote", "$95k – $120k", 88, 8.1),
      Extra("Compose UI Engineer", "Blue Yonder Air", "Hyderabad", null, 61, 5.8),
      Extra("Principal Android Engineer", "Wide World Importers", "Remote, EU", null, 77, 7.0),
      Extra("Mobile Developer (KMP)", "Alpine Ski House", "Pune", "₹30L – ₹42L", 83, 7.8),
    )

  /** Moves the sample dates to today, so the jobs read as just found. */
  private val shift = (System.currentTimeMillis() - SampleData.NOW) / HOUR * HOUR

  val jobs: List<Job> =
    (SampleData.jobs +
        extra.mapIndexed { i, e ->
          val id = 7L + i
          SampleData.jobs[0].copy(
            id = id,
            externalId = "ext-$id",
            externalUrl = "https://example.com/jobs/$id",
            siteId = 1L + i % 3,
            linkId = 1L + i % 3,
            title = e.title,
            companyName = e.company,
            location = e.location,
            salary = e.salary,
            workMode = if (e.location.startsWith("Remote")) WorkMode.REMOTE else WorkMode.HYBRID,
            tags = listOf("Kotlin"),
            status = JobStatus.NEW,
            ats = AtsSummary(overall = e.score, atsMatch = e.match, shortlisted = e.match >= 75),
            createdAt = SampleData.NOW - (i + 1) * 5 * HOUR / 2,
            updatedAt = SampleData.NOW - (i + 1) * 5 * HOUR / 2,
          )
        })
      .map { it.copy(createdAt = it.createdAt + shift, updatedAt = it.updatedAt + shift) }

  fun backend(): FakeBackend =
    FakeBackend(
      jobs = jobs,
      reviews =
        jobs
          .filter { it.ats != null }
          .associate { job ->
            job.id to SampleData.review.copy(jobId = job.id, summary = job.ats!!)
          },
    )

  fun improvement(jobId: Long): ResumeImprovement? =
    if (jobs.none { it.id == jobId && it.ats != null }) null
    else
      ResumeImprovement(
        jobId = jobId,
        keywords = SampleData.review.keywords,
        edits =
          listOf(
            ResumeEdit(
              1,
              ResumeSection.SUMMARY,
              "Android engineer with 7 years in Kotlin, KMP and Jetpack Compose.",
              "Senior Android engineer with 7 years in Kotlin, Kotlin Multiplatform (KMP) and " +
                "Jetpack Compose.",
              "Writes the job's term out in full and leads with its title.",
              gains = listOf("Kotlin Multiplatform"),
            ),
            ResumeEdit(
              2,
              ResumeSection.EXPERIENCE,
              "Worked with product and design on the payments flow.",
              "Worked with product, design and business stakeholders on the payments flow.",
              "Names the stakeholders the job asks for.",
              gains = listOf("Stakeholders"),
            ),
            ResumeEdit(
              3,
              ResumeSection.SKILLS,
              "REST, Retrofit, Ktor, Room",
              "REST, GraphQL (Apollo), Retrofit, Ktor, Room",
              "GraphQL is in the job; add it only if you used it.",
              gains = listOf("GraphQL"),
              newSkills = listOf("GraphQL"),
            ),
          ),
        createdAt = SampleData.NOW - HOUR,
      )

  const val WHY_US =
    "Northwind's offline-first field apps are the kind of Android work I enjoy most, and your " +
      "move to Kotlin Multiplatform matches the last two years of my work."

  const val RESUME_TEXT =
    "Sam Sample\nSenior Android engineer with 7 years in Kotlin, Kotlin Multiplatform (KMP) and " +
      "Jetpack Compose."

  private class Extra(
    val title: String,
    val company: String,
    val location: String,
    val salary: String?,
    val match: Int,
    val score: Double,
  )
}
