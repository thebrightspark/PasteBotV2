package brightspark.pastebotv2

import brightspark.pastebotv2.pastebin.PastebinService
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.event
import dev.kord.core.Kord
import dev.kord.core.behavior.reply
import dev.kord.core.entity.Attachment
import dev.kord.core.entity.ReactionEmoji
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.event.message.ReactionAddEvent
import dev.kord.rest.builder.message.create.allowedMentions
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

object PasteExtension : Extension() {
	private val EMOJI = ReactionEmoji.Unicode("\uD83D\uDDD2️") // https://emojipedia.org/spiral-notepad/
	private val LOG = KotlinLogging.logger {}
	private val MESSAGE_LOCKS = ConcurrentHashMap.newKeySet<ULong>()

	override val name: String = "paste"

	override suspend fun setup() {
		event<MessageCreateEvent> {
			check {
				failIf {
					event.member?.isBot == true || event.message.run { webhookId != null || attachments.isEmpty() }
				}
			}
			action {
				val message = event.message
				val messageId = message.id.value
				val attachments = message.attachments
				LOG.debug { "MessageCreateEvent (Message $messageId): Has attachments -> ${attachments.toLogString()}" }

				val textFiles = attachments.filter { it.isTextFile() }
				if (textFiles.isEmpty())
					LOG.debug { "MessageCreateEvent (Message $messageId): No text attachments" }
				else
					LOG.info { "MessageCreateEvent (Message $messageId): Has text attachments -> ${textFiles.toLogString()}" }

				kord.launch {
					message.addReaction(EMOJI)
					LOG.debug { "MessageCreateEvent (Message $messageId): Added reaction" }
				}
			}
		}

		event<ReactionAddEvent> {
			check {
				failIf {
					event.getUserOrNull()?.isBot == true
						|| event.message.getReactors(EMOJI).firstOrNull { it.id == kord.selfId } == null
						|| MESSAGE_LOCKS.contains(event.messageId.value)
				}
			}
			action {
				// If message is currently being handled, don't do anything
				val messageIdLong = event.messageId.value
				if (!MESSAGE_LOCKS.add(messageIdLong))
					return@action

				// Delete our reaction, so we don't try to create a paste again for the same message
				kord.launch {
					event.message.deleteOwnReaction(EMOJI)
					MESSAGE_LOCKS.remove(messageIdLong)
					LOG.debug { "ReactionAddEvent (Message $messageIdLong): Removed reaction" }
				}

				// Sanity check attachments
				val message = event.getMessage()
				val attachments = message.attachments
				val textFiles = attachments.filter { it.isTextFile() }
				if (textFiles.isEmpty())
					LOG.debug { "ReactionAddEvent (Message $messageIdLong): No text attachments" }
				else
					LOG.info { "ReactionAddEvent (Message $messageIdLong): Has text attachments -> ${textFiles.toLogString()}" }

				// Create pastes and reply
				kord.launch {
					val pastesMessage = textFiles
						.map {
							async {
								LOG.debug { "ReactionAddEvent (Message $messageIdLong): Handling file ${it.filename}" }
								val filename = it.filename
								val contents = getFileContents(kord, it.url)
								val pasteUrl = createPaste(kord, filename, contents)
								return@async filename to pasteUrl
							}
						}
						.awaitAll()
						.joinToString("\n\n") { (filename, pasteUrl) -> "$filename\n$pasteUrl" }

					message.reply {
						allowedMentions {
							repliedUser = true
						}
						content = pastesMessage
					}
					LOG.debug { "ReactionAddEvent (Message $messageIdLong): Replied to message:\n$pastesMessage" }
				}
			}
		}
	}

	private fun Attachment.isTextFile(): Boolean = !this.isImage && this.contentType?.startsWith("text") == true

	private fun Collection<Attachment>.toLogString(): String =
		this.joinToString { "'${it.filename}' [${it.contentType}]" }

	private suspend fun getFileContents(kord: Kord, url: String): String =
		kord.resources.httpClient.get(url).bodyAsText()

	private suspend fun createPaste(kord: Kord, filename: String, contents: String): String =
		PastebinService.createPaste(kord.resources.httpClient, filename, contents)
}
