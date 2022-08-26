package brightspark.pastebotv2

object EnvVars {
	private const val PREFIX = "PASTEBOTV2_"

	val DISCORD_TOKEN = env("BOT_TOKEN")
	val PASTEBIN_KEY = env("PASTEBIN_KEY")

	private fun env(name: String): String =
		com.kotlindiscord.kord.extensions.utils.env(PREFIX + name)
}
