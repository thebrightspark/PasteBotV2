package brightspark.pastebotv2

import brightspark.pastebotv2.extension.GeneralExtension
import brightspark.pastebotv2.extension.PasteExtension
import brightspark.pastebotv2.extension.TestingExtension
import brightspark.pastebotv2.util.EnvVars
import com.kotlindiscord.kord.extensions.ExtensibleBot
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent

@OptIn(PrivilegedIntent::class)
suspend fun main() {
	ExtensibleBot(EnvVars.DISCORD_TOKEN) {
		intents(addDefaultIntents = false, addExtensionIntents = false) {
			+Intent.GuildMessages
			+Intent.MessageContent
			+Intent.GuildMessageReactions
		}
		extensions {
			add { GeneralExtension }
			add { PasteExtension }
			if (EnvVars.TESTING)
				add { TestingExtension }
		}
		// Keeping this here just in-case I need to debug Pastebin requests
//		kord {
//			httpClient = io.ktor.client.HttpClient(io.ktor.client.engine.cio.CIO) {
//				install(io.ktor.client.plugins.logging.Logging) {
//					level = io.ktor.client.plugins.logging.LogLevel.ALL
//					filter { request ->
//						request.url.host.contains("pastebin.com")
//					}
//				}
//			}
//		}
	}.start()
}
