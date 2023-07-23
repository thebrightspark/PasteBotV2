package brightspark.pastebotv2.pastesite

import com.kotlindiscord.kord.extensions.koin.KordExKoinComponent
import dev.kord.core.Kord
import dev.kord.core.entity.Attachment
import io.ktor.client.*
import io.ktor.client.statement.*
import mu.KotlinLogging
import org.koin.core.component.inject

abstract class PasteSiteService(val fileSizeLimitBytes: Int) : KordExKoinComponent {
	private val log = KotlinLogging.logger {}
	private val kord: Kord by inject<Kord>()
	protected val httpClient: HttpClient
		get() = kord.resources.httpClient

	fun validateSize(attachment: Attachment): Boolean = attachment.size <= fileSizeLimitBytes

	abstract suspend fun create(filename: String, contents: String): String

	protected fun logStart(filename: String, contents: String): Unit =
		log.info { "create ($filename): Creating paste with ${contents.length} characters of content" }

	protected fun logSuccess(filename: String, result: String): Unit =
		log.info { "create ($filename): Created paste -> $result" }

	protected fun logFailure(filename: String, response: HttpResponse, result: String): Unit =
		log.warn { "create ($filename): Failed to create paste (${response.status}) -> $result" }
}
