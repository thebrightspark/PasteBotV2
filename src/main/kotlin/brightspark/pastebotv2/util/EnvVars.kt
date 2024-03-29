package brightspark.pastebotv2.util

object EnvVars {
	private const val PREFIX = "PASTEBOTV2_"

	val TESTING = envOrNull("TESTING")?.equals("true", true) ?: false
	val DISCORD_TOKEN = env("BOT_TOKEN")
	val PASTEBIN_KEY = env("PASTEBIN_KEY")

	private fun env(name: String): String =
		com.kotlindiscord.kord.extensions.utils.env(PREFIX + name)

	private fun envOrNull(name: String): String? =
		com.kotlindiscord.kord.extensions.utils.envOrNull(PREFIX + name)
}
