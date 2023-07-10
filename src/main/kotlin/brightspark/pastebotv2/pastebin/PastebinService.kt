package brightspark.pastebotv2.pastebin

import brightspark.pastebotv2.util.EnvVars
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import mu.KotlinLogging

object PastebinService {
	private val LOG = KotlinLogging.logger {}
	private const val URL = "https://pastebin.com/api/api_post.php"
	private val DEFAULT_PARAMS = listOf(
		"api_dev_key" to EnvVars.PASTEBIN_KEY,
		"api_option" to "paste",
		"api_paste_private" to "0",
		"api_paste_expire_date" to PastebinExpiry.ONE_WEEK.shortName
	)

	suspend fun createPaste(httpClient: HttpClient, filename: String, contents: String): String {
		LOG.info { "createPaste ($filename): Creating paste with ${contents.length} characters of content" }
		return httpClient
			.submitForm(URL, buildPastebinParams(filename, contents)) {
				accept(ContentType.Any)
			}
			.let { if (it.status.isSuccess()) it.bodyAsText() else it.status.toString() }
			.also { LOG.info { "createPaste ($filename): Created paste $it" } }
	}

	private fun buildPastebinParams(filename: String, contents: String): Parameters = Parameters.build {
		DEFAULT_PARAMS.forEach { append(it.first, it.second) }
		append("api_paste_name", filename)
		PastebinFormat.getForFilename(filename)?.let { append("api_paste_format", it.format) }
		append("api_paste_code", contents)
	}
}
