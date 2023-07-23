package brightspark.pastebotv2.extension

import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicMessageCommand
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.utils.getJumpUrl
import mu.KotlinLogging

object TestingExtension : Extension() {
	private val LOG = KotlinLogging.logger {}

	override val name: String = "testing"

	override suspend fun setup() {
		publicMessageCommand {
			name = "🔧 Add Reaction"
			action {
				val message = event.interaction.target
				message.addReaction(PasteExtension.EMOJI)
				respond { content = "Added reaction to message ${message.asMessage().getJumpUrl()}" }
				LOG.info { "Add Reaction: Added reaction to message ${message.id}" }
			}
		}
	}
}
