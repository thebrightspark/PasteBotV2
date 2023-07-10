package brightspark.pastebotv2.pastebin

class PastebinFormat private constructor(
	val format: String,
	val name: String,
	val extensions: Array<out String>
) {
	companion object {
		private fun create(format: String, name: String, vararg extensions: String): PastebinFormat =
			PastebinFormat(format, name, extensions)

		private val FORMATS: List<PastebinFormat> = listOf(
			create("bash", "Bash", "sh"),
			create("dos", "Batch", "bat"),
			create("c", "C", "c"),
			create("csharp", "C#", "cs"),
			create("cpp", "C++", "cpp"),
			create("cmake", "CMake", "cmake"),
			create("css", "CSS", "css"),
			create("dart", "Dart", "dart"),
			create("diff", "Diff", "diff"),
			create("erlang", "Erlang", "erl", "hrl"),
			create("gml", "Game Maker", "gml"),
			create("gdscript", "GDScript", "gdc"),
			create("go", "Go", "go"),
			create("groovy", "Groovy", "groovy", "gvy", "gy", "gsh"),
			create("haskell", "Haskell", "hs"),
			create("html5", "HTML 5", "html"),
			create("ini", "INI file", "ini"),
			create("java", "Java", "java"),
			create("javascript", "JavaScript", "js"),
			create("json", "JSON", "json"),
			create("kotlin", "Kotlin", "kt"),
			create("lisp", "Lisp", "lisp"),
			create("lolcode", "LOL Code", "lol", "lols"),
			create("lua", "Lua", "lua"),
			create("make", "Make", "make"),
			create("markdown", "Markdown", "md"),
			create("matlab", "MatLab", "m", "mat"),
			create("glsl", "OpenGL Shading", "glsl", "vert", "tesc", "tese", "geom", "frag", "comp"),
			create("pascal", "Pascal", "pas"),
			create("perl", "Perl", "pl"),
			create("php", "PHP", "php"),
			create("powershell", "PowerShell", "ps1"),
			create("properties", "Properties", "properties"),
			create("python", "Python", "py"),
			create("ruby", "Ruby", "rb"),
			create("rust", "Rust", "rs"),
			create("scala", "Scala", "scala", "sc"),
			create("sql", "SQL", "sql"),
			create("swift", "Swift", "swift"),
			create("typescript", "TypeScript", "ts"),
			create("uscript", "UnrealScript", "uc"),
			create("xml", "XML", "xml"),
			create("yaml", "YAML", "yaml")
		)

		private val extensionToFormatMap: Map<String, PastebinFormat> = buildMap {
			FORMATS.forEach { format ->
				format.extensions.forEach {
					put(it, format)
				}
			}
		}

		fun getForFilename(filename: String): PastebinFormat? {
			val extension = filename.substringAfterLast('.', "")
			return if (extension.isNotBlank()) extensionToFormatMap[extension] else null
		}
	}
}
