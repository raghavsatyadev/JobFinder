package io.github.raghavsatyadev.jobfinder.server.parsing.sites

import io.github.raghavsatyadev.jobfinder.server.parsing.SelectorDescriptionParser

/**
 * LinkedIn job page description (old `parseLinkedinJobDescription`). Covers the guest job page, the
 * logged-in job view and the SDUI "About the job" block.
 *
 * LinkedIn puts `<br>`s inside `<strong>` and at the end of `<li>`; they are moved out or dropped
 * first so the Markdown has no `**Heading\n\n**` or blank list items.
 */
object LinkedInDescriptionParser :
  SelectorDescriptionParser(
    selectors =
      listOf(
        ".description__text .show-more-less-html__markup",
        ".jobs-box__html-content > .job-details-module__content",
        ".jobs-description__container .jobs-box__html-content",
        ".job-details-module.artdeco-card",
        "div[data-sdui-component=\"com.linkedin.sdui.generated.jobseeker.dsl.impl.aboutTheJob\"]",
      ),
    rewriteHtml = { html ->
      html
        .replace("<br><br></strong>", "</strong><br><br>")
        .replace("<br></strong>", "</strong><br>")
        .replace(Regex("(<br>)+</li>"), "</li>")
    },
  )
