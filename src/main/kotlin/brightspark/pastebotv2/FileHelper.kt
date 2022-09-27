package brightspark.pastebotv2

import dev.kord.core.Kord
import dev.kord.core.entity.Attachment
import io.ktor.client.call.*
import io.ktor.client.request.*
import java.util.zip.GZIPInputStream

object FileHelper {
	private const val FILE_SIZE_LIMIT = 500_000 // 0.5MB
	private const val MIME_TEXT = "text/"
	private const val FILE_EXT_GZIP = ".gz"

	fun isValidFile(attachment: Attachment): Boolean =
		!attachment.isImage && attachment.size <= FILE_SIZE_LIMIT && attachment.contentType?.startsWith(MIME_TEXT) ?: false

	suspend fun getFileContents(kord: Kord, attachment: Attachment): String {
		val filename = attachment.filename
		val mimeType = attachment.contentType!!

		if (!isValidFile(attachment))
			error("Invalid file type $filename ('$mimeType')")

		val url = attachment.url
		var contentsBytes = getFileContents<ByteArray>(kord, url)
		if (filename.endsWith(FILE_EXT_GZIP)) {
			contentsBytes = decompressGzip(contentsBytes)

			val bytes = contentsBytes.size
			if (bytes > FILE_SIZE_LIMIT)
				return "Decompressed file too large! (${bytesToMegabytesText(bytes)} MB)"
		}

		return String(contentsBytes)
	}

	private suspend inline fun <reified T> getFileContents(kord: Kord, url: String): T =
		kord.resources.httpClient.get(url).body()

	private fun decompressGzip(compressedContents: ByteArray): ByteArray =
		GZIPInputStream(compressedContents.inputStream()).use { it.readAllBytes() }

	private fun bytesToMegabytesText(bytes: Int): String =
		String.format("%.1f", bytes.toFloat() / 1_000_000f)
}
