package io.github.raghavsatyadev.jobfinder.server.http

import io.github.raghavsatyadev.jobfinder.server.service.ApiException
import io.github.raghavsatyadev.jobfinder.shared.api.ApiErrorCode
import io.github.raghavsatyadev.jobfinder.shared.model.AiPrompt
import io.github.raghavsatyadev.jobfinder.shared.model.PromptEdit
import io.github.raghavsatyadev.jobfinder.shared.model.PromptId
import io.github.raghavsatyadev.jobfinder.shared.model.PromptTry
import io.github.raghavsatyadev.jobfinder.shared.repository.AiPromptsRepository
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/**
 * `GET /prompts`, `PUT /prompts/{id}`: the AI instructions, edited or back to the default. `POST
 * /prompts/{id}/try` runs instructions once on a job without saving them.
 */
fun Route.promptRoutes(deps: ApiDependencies) {
  route("/prompts") {
    get { call.respondOk(prompts(deps).listPrompts()) }
    put("/{id}") {
      val id = call.parameters["id"]?.let(PromptId::fromWire) ?: badRequest("Unknown prompt.")
      val text = call.receive<PromptEdit>().text
      if (text != null && text.length > AiPrompt.MAX_LENGTH) {
        badRequest("Instructions can be up to ${AiPrompt.MAX_LENGTH} characters.")
      }
      val prompts = prompts(deps)
      call.respondOk(if (text == null) prompts.resetPrompt(id) else prompts.savePrompt(id, text))
    }
    post("/{id}/try") {
      val id = call.parameters["id"]?.let(PromptId::fromWire) ?: badRequest("Unknown prompt.")
      val text = call.receive<PromptTry>().text
      if (text.length > AiPrompt.MAX_LENGTH) {
        badRequest("Instructions can be up to ${AiPrompt.MAX_LENGTH} characters.")
      }
      call.respondOk(prompts(deps).tryPrompt(id, text))
    }
  }
}

private fun prompts(deps: ApiDependencies): AiPromptsRepository =
  deps.prompts ?: throw ApiException(ApiErrorCode.UNAVAILABLE)
