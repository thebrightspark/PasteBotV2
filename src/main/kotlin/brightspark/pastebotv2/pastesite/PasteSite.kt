package brightspark.pastebotv2.pastesite

import brightspark.pastebotv2.pastesite.mclogs.MclogsService
import brightspark.pastebotv2.pastesite.pastebin.PastebinService
import brightspark.pastebotv2.pastesite.pastegg.PasteggService
import brightspark.pastebotv2.util.FileHelper
import dev.kord.core.entity.Message

enum class PasteSite(val siteName: String, val service: PasteSiteService, vararg val preferredExtensions: String) {
	MC_LOGS("mclo.gs", MclogsService, ".*\\.log(?:\\.gz)?$"),
	PASTE_GG("paste.gg", PasteggService),
	PASTEBIN("Pastebin.com", PastebinService);

	val maxSizeMb: String = FileHelper.bytesToMegabytesText(service.fileSizeLimitBytes)

	companion object {
		private val DEFAULT = PASTE_GG

		private fun getPreferred(fileName: String): PasteSite = values()
			.firstOrNull { pasteSite -> pasteSite.preferredExtensions.any { fileName.matches(Regex(it)) } }
			?: DEFAULT

		fun getPreferred(message: Message): PasteSite {
			val fileNames = message.attachments.map { it.filename }
			return fileNames.map { getPreferred(it) }.minBy { it.ordinal }
		}
	}
}
