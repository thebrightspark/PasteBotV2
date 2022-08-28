package brightspark.pastebotv2

import com.kotlindiscord.kord.extensions.checks.types.CheckContext
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.event
import dev.kord.core.entity.Guild
import dev.kord.core.entity.User
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.guild.GuildCreateEvent
import dev.kord.core.event.guild.GuildDeleteEvent
import kotlinx.coroutines.flow.map
import mu.KotlinLogging

object GeneralExtension : Extension() {
	private val LOG = KotlinLogging.logger {}

	override val name: String = "general"

	override suspend fun setup() {
		event<ReadyEvent> {
			check { isLogInfoEnabled() }
			action {
				val numGuilds = event.guildIds.size
				LOG.info { "ReadyEvent: Ready with $numGuilds guild${if (numGuilds != 1) "s" else ""}" }

				val sb = StringBuilder()
				event.getGuilds().map { it.toLogString() }.collect { sb.append('\n').append(it) }
				val guildString = sb.toString()
				LOG.info { "ReadyEvent: Guilds:$guildString" }
			}
		}

		event<GuildCreateEvent> {
			check { isLogInfoEnabled() }
			action {
				// TODO: Make use of GuildCreateEvent#unavailable once it's added
				val guildString = event.guild.toLogString()
				LOG.info { "GuildCreateEvent: Connected to guild $guildString" }
			}
		}

		event<GuildDeleteEvent> {
			check { isLogInfoEnabled() }
			action {
				val guildString = event.guild.toLogString()
				LOG.info { "GuildDeleteEvent: Disconnected from ${event.unavailable.unavailableString()} guild $guildString" }
			}
		}
	}

	private suspend fun CheckContext<*>.isLogInfoEnabled() {
		passIf { LOG.isInfoEnabled }
	}

	private fun Boolean.unavailableString(): String = if (this) "unavailable" else ""

	private suspend fun Guild?.toLogString(): String =
		this?.let { guild -> "${guild.name} [${guild.id}] (Owner: ${guild.owner.asUser().toLogString()})" } ?: "<null>"

	private fun User.toLogString(): String = "$username#$discriminator"
}
