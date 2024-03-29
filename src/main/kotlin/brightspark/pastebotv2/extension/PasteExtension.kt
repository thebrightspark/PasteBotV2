package brightspark.pastebotv2.extension

import brightspark.pastebotv2.pastesite.PasteSite
import brightspark.pastebotv2.util.FileHelper
import com.kotlindiscord.kord.extensions.checks.guildFor
import com.kotlindiscord.kord.extensions.checks.isNotBot
import com.kotlindiscord.kord.extensions.checks.types.CheckContext
import com.kotlindiscord.kord.extensions.commands.application.message.PublicMessageCommandContext
import com.kotlindiscord.kord.extensions.components.components
import com.kotlindiscord.kord.extensions.components.ephemeralButton
import com.kotlindiscord.kord.extensions.components.forms.ModalForm
import com.kotlindiscord.kord.extensions.events.EventContext
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.extensions.publicMessageCommand
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.utils.getJumpUrl
import com.kotlindiscord.kord.extensions.utils.hasPermissions
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.MessageBehavior
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.reply
import dev.kord.core.entity.Attachment
import dev.kord.core.entity.Message
import dev.kord.core.entity.ReactionEmoji
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.event.message.ReactionAddEvent
import dev.kord.rest.builder.message.create.MessageCreateBuilder
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

object PasteExtension : Extension() {
	private const val MESSAGE_HAS_NONE_WITHIN_SIZE_LIMIT = "This message has no file attachments within the size limit!"
	private const val MESSAGE_HAS_LOCK = "Already creating a paste for this message!"

	val EMOJI = ReactionEmoji.Unicode("\uD83D\uDDD2️") // https://emojipedia.org/spiral-notepad/
	private val LOG = KotlinLogging.logger {}
	private val MESSAGE_LOCKS = ConcurrentHashMap.newKeySet<ULong>()

	override val name: String = "paste"

	override suspend fun setup() {
		event<MessageCreateEvent> {
			check { isNotBot() }
			check { botHasPermissions(Permission.AddReactions, Permission.SendMessages) }
			action { onMessageCreate() }
		}

		event<ReactionAddEvent> {
			check { isNotBot() }
			check { botHasPermissions(Permission.SendMessages) }
			check { failIf { event.message.getReactors(EMOJI).firstOrNull { it.id == kord.selfId } == null } }
			check { messageHasLock(event.messageId) }
			action { handleReactionAddEvent() }
		}

		PasteSite.values().forEach { pasteSite ->
			publicMessageCommand {
				name = "Upload ${pasteSite.siteName}"
				check { isNotBot() }
				check { botHasPermissions(Permission.SendMessages) }
				check { messageHasLock(event.interaction.targetId) }
				action { handleMessageCommand(pasteSite) }
			}
		}
	}

	private fun EventContext<MessageCreateEvent>.onMessageCreate() {
		val message = event.message
		val messageId = message.id.value
		val logPrefix: String by lazy { "MessageCreateEvent (Message $messageId):" }

		addReactionToMessageWithTextFiles(message, logPrefix)
	}

	private fun addReactionToMessageWithTextFiles(message: Message, logPrefix: String) {
		if (message.webhookId != null || message.attachments.isEmpty()) return

		val attachments = message.attachments
		LOG.debug { "$logPrefix Has attachments -> ${attachments.toLogString()}" }

		val textFiles = attachments.filter { FileHelper.isValidContentType(it) }
		if (textFiles.isEmpty()) {
			LOG.debug { "$logPrefix No text attachments" }
			return
		}

		LOG.info { "$logPrefix Has text attachments -> ${textFiles.toLogString()}" }

		kord.launch {
			message.addReaction(EMOJI)
			LOG.debug { "$logPrefix Added reaction" }
		}
	}

	private suspend fun EventContext<ReactionAddEvent>.handleReactionAddEvent() {
		LOG.info { "handleReactionAddEvent: Received reaction for message ${event.messageId}" }

		val messageBehaviour = event.message
		val messageIdLong = messageBehaviour.id.value

		// If message is currently being handled, don't do anything
		if (!tryLockMessage(messageIdLong)) {
			messageBehaviour.reply { content = MESSAGE_HAS_LOCK }
			return
		}

		removeBotReaction(messageBehaviour)

		val pasteSite = PasteSite.getPreferred(messageBehaviour.asMessage())
		LOG.debug { "handleReactionAddEvent: Selected $pasteSite to upload pastes to" }

		val attachments = getAttachments(pasteSite, messageBehaviour)
		val attachmentsWithinSizeLimit = attachments.first
		if (attachmentsWithinSizeLimit.isEmpty()) {
			// No files within size limit!
			messageBehaviour.reply { content = MESSAGE_HAS_NONE_WITHIN_SIZE_LIMIT }
			return
		}
		val textFiles = attachments.second

		if (textFiles.size < attachmentsWithinSizeLimit.size) {
			// If any attachments have invalid content types, ask user if they want to force upload
			messageBehaviour.reply {
				createAskUploadMessage(pasteSite, messageIdLong, null, attachmentsWithinSizeLimit, textFiles)
			}
		} else {
			handleUpload(pasteSite, messageIdLong, textFiles) {
				messageBehaviour.reply { content = it }
			}
		}
	}

	private suspend fun PublicMessageCommandContext<ModalForm>.handleMessageCommand(pasteSite: PasteSite) {
		LOG.info { "handleMessageCommand: Received message command for message ${event.interaction.targetId}" }

		val messageBehaviour = event.interaction.target
		val messageIdLong = messageBehaviour.id.value
		val jumpText = "[Link To Message](${messageBehaviour.asMessage().getJumpUrl()})"

		// If message is currently being handled, don't do anything
		if (!tryLockMessage(messageIdLong)) {
			respond { content = "$jumpText\n\n$MESSAGE_HAS_LOCK" }
			return
		}

		removeBotReaction(messageBehaviour)

		val attachments = getAttachments(pasteSite, messageBehaviour)
		val attachmentsWithinSizeLimit = attachments.first
		if (attachmentsWithinSizeLimit.isEmpty()) {
			// No files within size limit!
			respond { content = "$jumpText\n\n$MESSAGE_HAS_NONE_WITHIN_SIZE_LIMIT (Max ${pasteSite.maxSizeMb})" }
			return
		}
		val textFiles = attachments.second

		if (textFiles.size < attachmentsWithinSizeLimit.size) {
			// If any attachments have invalid content types, ask user if they want to force upload
			respond {
				createAskUploadMessage(pasteSite, messageIdLong, jumpText, attachmentsWithinSizeLimit, textFiles)
			}
		} else {
			handleUpload(pasteSite, messageIdLong, textFiles) {
				respond { content = "$jumpText\n\n$it" }
			}
		}
	}

	private fun tryLockMessage(messageId: ULong): Boolean = MESSAGE_LOCKS.add(messageId)

	private fun removeBotReaction(message: MessageBehavior) {
		kord.launch {
			message.deleteOwnReaction(EMOJI)
			LOG.debug { "removeBotReaction (Message ${message.id}): Removed reaction" }
		}
	}

	private suspend fun getAttachments(
		pasteSite: PasteSite,
		messageBehaviour: MessageBehavior
	): Pair<List<Attachment>, List<Attachment>> {
		val messageId = messageBehaviour.id.value
		val pasteSiteService = pasteSite.service
		val attachments = messageBehaviour.asMessage().attachments
		val attachmentsWithinSizeLimit = attachments.filter { pasteSiteService.validateSize(it) }
		val textFiles = attachmentsWithinSizeLimit.filter { FileHelper.isValidContentType(it) }
		if (LOG.isDebugEnabled) {
			if (attachmentsWithinSizeLimit.isNotEmpty())
				LOG.debug { "handleMessage (Message $messageId): Attachments -> ${attachmentsWithinSizeLimit.toLogString()}" }
		} else {
			if (textFiles.isEmpty())
				LOG.debug { "handleMessage (Message $messageId): No text attachments" }
			else
				LOG.info { "handleMessage (Message $messageId): Has text attachments -> ${textFiles.toLogString()}" }
		}
		return attachmentsWithinSizeLimit to textFiles
	}

	private suspend inline fun MessageCreateBuilder.createAskUploadMessage(
		pasteSite: PasteSite,
		messageIdLong: ULong,
		jumpText: String?,
		attachmentsWithinSizeLimit: List<Attachment>,
		textFiles: List<Attachment>
	) {
		val validFiles = if (textFiles.isEmpty()) "*None*" else textFiles.joinToString("\n") { "- `${it.filename}`" }
		val invalidFiles = attachmentsWithinSizeLimit
			.filter { !textFiles.contains(it) }
			.joinToString("\n") { "- `${it.filename}`" }

		content = """
				${jumpText ?: ""}
				
				The following files are valid to upload:
				$validFiles
				However the following seem invalid:
				$invalidFiles
				
				Would you like to upload the invalid files too regardless?
			""".trimIndent().trim()
		components {
			ephemeralButton {
				label = "Yes"
				style = ButtonStyle.Success
				action {
					handleUpload(pasteSite, messageIdLong, attachmentsWithinSizeLimit) {
						this.message.edit {
							content = "$jumpText\n\n$it"
							components { /* None */ }
						}
					}
				}
			}
			ephemeralButton {
				label = "No"
				style = ButtonStyle.Danger
				action {
					if (textFiles.isNotEmpty()) {
						handleUpload(pasteSite, messageIdLong, textFiles) {
							this.message.edit {
								content = "$jumpText\n\n$it"
								components { /* None */ }
							}
						}
					} else {
						MESSAGE_LOCKS.remove(messageIdLong)
						this.message.edit {
							content = "$jumpText\n\nNothing to upload"
							components { /* None */ }
						}
					}
				}
			}
		}
	}

	// Create pastes and reply
	private suspend fun handleUpload(
		pasteSite: PasteSite,
		messageId: ULong,
		textFiles: List<Attachment>,
		pastesMessageConsumer: suspend (String) -> Unit
	) {
		kord.launch {
			val pastesMessage = textFiles.map { async { uploadFile(pasteSite, messageId, it) } }.awaitAll()
				.joinToString("\n\n") { (filename, pasteUrl) -> "$filename\n$pasteUrl" }

			pastesMessageConsumer(pastesMessage)
			MESSAGE_LOCKS.remove(messageId)
			LOG.debug { "handleUpload (Message $messageId): Replied to message:\n$pastesMessage" }
		}
	}

	private suspend fun uploadFile(
		pasteSite: PasteSite,
		messageId: ULong,
		attachment: Attachment
	): Pair<String, String> {
		val filename = attachment.filename
		return try {
			LOG.debug { "handleUpload (Message $messageId): Handling upload $filename to ${pasteSite.siteName}" }
			val contents = FileHelper.getFileContents(kord, attachment)
			val pasteUrl = pasteSite.service.create(filename, contents)
			filename to pasteUrl
		} catch (e: Throwable) {
			LOG.error(e) { "handleUpload (Message $messageId): Failed to upload $filename to ${pasteSite.siteName}" }
			filename to "${e::class.simpleName}: ${e.message}"
		}
	}

	private suspend fun CheckContext<*>.messageHasLock(messageId: Snowflake) {
		failIf(MESSAGE_HAS_LOCK) { MESSAGE_LOCKS.contains(messageId.value) }
	}

	private suspend fun CheckContext<*>.botHasPermissions(vararg permissions: Permission) {
		passIf { guildFor(event)?.getMemberOrNull(kord.selfId)?.hasPermissions(*permissions) ?: false }
	}

	private fun Collection<Attachment>.toLogString(): String =
		this.joinToString { "'${it.filename}' [${it.contentType}]" }
}
