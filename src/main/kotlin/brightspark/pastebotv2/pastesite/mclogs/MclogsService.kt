package brightspark.pastebotv2.pastesite.mclogs

import brightspark.pastebotv2.pastesite.PasteSiteService
import io.ktor.client.call.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

// https://api.mclo.gs/
object MclogsService : PasteSiteService(10_000_000) {
	private const val API_URL = "https://api.mclo.gs/1/log"

	override suspend fun create(filename: String, contents: String): String {
		logStart(filename, contents)
		return httpClient.submitForm(API_URL, Parameters.build { append("content", contents) }) {
			contentType(ContentType.Application.FormUrlEncoded)
		}.let { response ->
			return@let if (response.status.isSuccess()) {
				val result = response.body<Result>()
				if (result.success)
					result.url!!.also { logSuccess(filename, it) }
				else
					result.error!!.also { logFailure(filename, response, it) }
			} else {
				response.status.toString().also { logFailure(filename, response, it) }
			}
		}
	}

	@Serializable
	data class Result(
		val success: Boolean,
		val id: String? = null,
		val url: String? = null,
		val raw: String? = null,
		val error: String? = null
	)
}
