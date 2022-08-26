@file:Suppress("unused")

package brightspark.pastebotv2.logback

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.pattern.color.ANSIConstants.*
import ch.qos.logback.core.pattern.color.ForegroundCompositeConverterBase

class HighlightConverter : ForegroundCompositeConverterBase<ILoggingEvent>() {
	override fun getForegroundColorCode(event: ILoggingEvent): String = when (event.level) {
		Level.ERROR -> RED_FG
		Level.WARN -> YELLOW_FG
		Level.INFO -> DEFAULT_FG
		Level.DEBUG -> BLUE_FG
		Level.TRACE -> CYAN_FG
		else -> DEFAULT_FG
	}
}
