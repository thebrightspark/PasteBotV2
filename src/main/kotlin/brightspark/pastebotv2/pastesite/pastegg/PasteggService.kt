package brightspark.pastebotv2.pastesite.pastegg

import brightspark.pastebotv2.pastesite.PasteSiteService
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.temporal.ChronoUnit

// https://github.com/ascclemens/paste/blob/master/api.md
object PasteggService : PasteSiteService(1_000_000) {
	private const val API_URL = "https://api.paste.gg/v1/pastes"
	private const val PASTE_URL = "https://paste.gg/p/anonymous/"

	private fun createRequestBody(filename: String, contents: String): Request = Request(
		filename,
		Instant.now().plus(7, ChronoUnit.DAYS).toString(),
		listOf(RequestFile(filename, RequestFileContent(contents)))
	)

	override suspend fun create(filename: String, contents: String): String {
		logStart(filename, contents)
		val httpClient = httpClient
		return httpClient.post(API_URL) {
			setBody(createRequestBody(filename, contents))
			contentType(ContentType.Application.Json)
		}.let { response ->
			return@let if (response.status.isSuccess()) {
				val result = response.body<Result>()
				if (result.status == ResultStatus.SUCCESS)
					"$PASTE_URL${result.result!!.id}".also { logSuccess(filename, it) }
				else
					"${result.error} - ${result.message}".also { logFailure(filename, response, it) }
			} else {
				logFailure(filename, response, response.bodyAsText())
				response.status.toString()
			}
		}
	}

	@Serializable
	data class Request(val name: String, val expires: String, val files: List<RequestFile>)

	@Serializable
	data class RequestFile(val name: String, val content: RequestFileContent)

	@Serializable
	data class RequestFileContent(val value: String, val format: String = "text")

	@Serializable
	data class Result(
		val status: ResultStatus,
		val result: ResultResult? = null,
		val error: String? = null,
		val message: String? = null
	)

	@Serializable
	data class ResultResult(
		val id: String,
		val name: String,
		val description: String?,
		val visibility: String,
		@SerialName("created_at")
		val createdAt: String,
		@SerialName("updated_at")
		val updatedAt: String,
		val expires: String,
		val files: List<ResultFile>,
		@SerialName("deletion_key")
		val deletionKey: String?
	)

	@Serializable
	enum class ResultStatus {
		@SerialName("success")
		SUCCESS,

		@SerialName("error")
		ERROR
	}

	@Serializable
	data class ResultFile(
		@SerialName("highlight_language")
		val highlightLanguage: String?,
		val id: String,
		val name: String
	)
}
