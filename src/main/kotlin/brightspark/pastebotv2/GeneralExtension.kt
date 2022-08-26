package brightspark.pastebotv2

import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.event
import dev.kord.core.event.gateway.ReadyEvent
import mu.KotlinLogging

object GeneralExtension : Extension() {
	private val LOG = KotlinLogging.logger {}

	override val name: String = "general"

	override suspend fun setup() {
		event<ReadyEvent> {
			action {
				val numGuilds = event.guildIds.size
				LOG.info { "ReadyEvent: Ready with $numGuilds guild${if (numGuilds != 1) "s" else ""}" }
			}
		}
	}
}
