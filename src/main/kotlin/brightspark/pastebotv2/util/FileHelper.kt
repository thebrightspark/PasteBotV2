package brightspark.pastebotv2.util

import dev.kord.core.Kord
import dev.kord.core.entity.Attachment
import io.ktor.client.call.*
import io.ktor.client.request.*
import java.util.zip.GZIPInputStream

object FileHelper {
	private const val MIME_TEXT = "text/"
	private const val MIME_JSON = "application/json"
	private const val FILE_EXT_GZIP = ".gz"

	fun isValidContentType(attachment: Attachment): Boolean =
		!attachment.isImage && attachment.contentType?.let { it.startsWith(MIME_TEXT) || it == MIME_JSON } ?: false

	suspend fun getFileContents(kord: Kord, attachment: Attachment): String =
		String(getFileContentsBytes(kord, attachment))

	suspend fun getFileContentsBytes(kord: Kord, attachment: Attachment): ByteArray {
		val filename = attachment.filename
		val url = attachment.url
		var contentsBytes: ByteArray = kord.resources.httpClient.get(url).body()
		if (filename.endsWith(FILE_EXT_GZIP)) {
			contentsBytes = GZIPInputStream(contentsBytes.inputStream()).use { it.readAllBytes() }
		}
		return contentsBytes
	}

	fun bytesToMegabytesText(bytes: Int): String =
		String.format("%.1f", bytes.toFloat() / 1_000_000f).replace(Regex("\\.0$"), "")
}
