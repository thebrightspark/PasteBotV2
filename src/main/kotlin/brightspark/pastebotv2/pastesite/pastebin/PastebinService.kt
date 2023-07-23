package brightspark.pastebotv2.pastesite.pastebin

import brightspark.pastebotv2.pastesite.PasteSiteService
import brightspark.pastebotv2.util.EnvVars
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*

// https://pastebin.com/doc_api
object PastebinService : PasteSiteService(500_000) {
	private const val API_URL = "https://pastebin.com/api/api_post.php"
	private val DEFAULT_PARAMS = listOf(
		"api_dev_key" to EnvVars.PASTEBIN_KEY,
		"api_option" to "paste",
		"api_paste_private" to "0",
		"api_paste_expire_date" to "1W"
	)

	private fun buildParams(filename: String, contents: String): Parameters = Parameters.build {
		DEFAULT_PARAMS.forEach { append(it.first, it.second) }
		append("api_paste_name", filename)
		PastebinFormat.getForFilename(filename)?.let { append("api_paste_format", it.format) }
		append("api_paste_code", contents)
	}

	override suspend fun create(filename: String, contents: String): String {
		logStart(filename, contents)
		return httpClient.submitForm(API_URL, buildParams(filename, contents)) { accept(ContentType.Any) }
			.let { response ->
				return@let if (response.status.isSuccess()) {
					response.bodyAsText().also {
						if (response.status.isSuccess())
							logSuccess(filename, it)
						else
							logFailure(filename, response, it)
					}
				} else {
					response.status.toString().also { logFailure(filename, response, it) }
				}
			}
	}
}
