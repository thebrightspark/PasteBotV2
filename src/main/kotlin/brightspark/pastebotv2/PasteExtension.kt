package brightspark.pastebotv2

import brightspark.pastebotv2.pastebin.PastebinService
import com.kotlindiscord.kord.extensions.checks.channelType
import com.kotlindiscord.kord.extensions.checks.guildFor
import com.kotlindiscord.kord.extensions.checks.hasPermission
import com.kotlindiscord.kord.extensions.checks.isNotBot
import com.kotlindiscord.kord.extensions.checks.types.CheckContext
import com.kotlindiscord.kord.extensions.events.EventContext
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.extensions.publicMessageCommand
import com.kotlindiscord.kord.extensions.utils.hasPermissions
import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.MessageBehavior
import dev.kord.core.behavior.reply
import dev.kord.core.entity.Attachment
import dev.kord.core.entity.Message
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
	private const val MESSAGE_HAS_NO_ATTACHMENTS = "This message has no text file attachments!"
	private const val MESSAGE_HAS_LOCK = "Already creating a paste for this message!"

	private val EMOJI = ReactionEmoji.Unicode("\uD83D\uDDD2️") // https://emojipedia.org/spiral-notepad/
	private val LOG = KotlinLogging.logger {}
	private val MESSAGE_LOCKS = ConcurrentHashMap.newKeySet<ULong>()

	override val name: String = "paste"

	override suspend fun setup() {
		event<MessageCreateEvent> {
			check { isNotBot() }
			check { channelType(ChannelType.GuildText) }
			check { hasPermission(Permission.AddReactions) }
			check { hasPermission(Permission.SendMessages) }
			check { failIf { event.message.run { webhookId != null || attachments.isEmpty() } } }
			action { onMessageCreate() }
		}

		event<ReactionAddEvent> {
			check { isNotBot() }
			check { failIf { event.message.getReactors(EMOJI).firstOrNull { it.id == kord.selfId } == null } }
			check { messageHasLock(event.messageId) }
			action {
				LOG.info { "Received reaction for message ${event.messageId}" }
				handleMessage(event.message) { event.getMessage() }
			}
		}

		publicMessageCommand {
			name = "Upload to Pastebin"
			check { isNotBot() }
			check { botHasPermissions(Permission.SendMessages) }
			check { hasPermission(Permission.SendMessages) }
			check { failIf(MESSAGE_HAS_NO_ATTACHMENTS) { event.interaction.getTarget().attachments.none { it.isTextFile() } } }
			check { messageHasLock(event.interaction.targetId) }
			action {
				LOG.info { "Received message command for message ${event.interaction.targetId}" }
				handleMessage(event.interaction.target) { event.interaction.getTarget() }
			}
		}
	}

	private suspend fun EventContext<MessageCreateEvent>.onMessageCreate() {
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

	private suspend fun handleMessage(messageBehaviour: MessageBehavior, messageSupplier: suspend () -> Message) {
		// If message is currently being handled, don't do anything
		val messageIdLong = messageBehaviour.id.value
		if (!MESSAGE_LOCKS.add(messageIdLong)) {
			messageBehaviour.reply {
				allowedMentions { repliedUser = true }
				content = MESSAGE_HAS_LOCK
			}
			return
		}

		// If the message has the reaction, remove it so that we don't try to create a paste again for the same message
		kord.launch {
			messageBehaviour.deleteOwnReaction(EMOJI)
			LOG.debug { "handleMessage (Message $messageIdLong): Removed reaction" }
		}

		// Sanity check attachments
		val message = messageSupplier()
		val attachments = message.attachments
		val textFiles = attachments.filter { it.isTextFile() }
		if (textFiles.isEmpty())
			LOG.debug { "handleMessage (Message $messageIdLong): No text attachments" }
		else
			LOG.info { "handleMessage (Message $messageIdLong): Has text attachments -> ${textFiles.toLogString()}" }

		// Create pastes and reply
		kord.launch {
			val pastesMessage = textFiles
				.map {
					async {
						LOG.debug { "handleMessage (Message $messageIdLong): Handling file ${it.filename}" }
						val filename = it.filename
						val contents = getFileContents(kord, it.url)
						val pasteUrl = createPaste(kord, filename, contents)
						return@async filename to pasteUrl
					}
				}
				.awaitAll()
				.joinToString("\n\n") { (filename, pasteUrl) -> "$filename\n$pasteUrl" }

			message.reply {
				allowedMentions { repliedUser = true }
				content = pastesMessage
			}
			MESSAGE_LOCKS.remove(messageIdLong)
			LOG.debug { "handleMessage (Message $messageIdLong): Replied to message:\n$pastesMessage" }
		}
	}

	private suspend fun CheckContext<*>.messageHasLock(messageId: Snowflake) {
		failIf(MESSAGE_HAS_LOCK) { MESSAGE_LOCKS.contains(messageId.value) }
	}

	private suspend fun CheckContext<*>.botHasPermissions(vararg permissions: Permission) {
		passIf { guildFor(event)?.getMemberOrNull(kord.selfId)?.hasPermissions(*permissions) ?: false }
	}

	private fun Attachment.isTextFile(): Boolean = !this.isImage && this.contentType?.startsWith("text") == true

	private fun Collection<Attachment>.toLogString(): String =
		this.joinToString { "'${it.filename}' [${it.contentType}]" }

	private suspend fun getFileContents(kord: Kord, url: String): String =
		kord.resources.httpClient.get(url).bodyAsText()

	private suspend fun createPaste(kord: Kord, filename: String, contents: String): String =
		PastebinService.createPaste(kord.resources.httpClient, filename, contents)
}
