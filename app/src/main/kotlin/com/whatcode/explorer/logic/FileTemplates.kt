package com.whatcode.explorer.logic

/**
 * Built-in new-file templates for the explorer's New file dialog. Derives identifier or heading names cleanly from
 * the typed file name. No Android here (unit tested).
 */
object FileTemplates {

    data class Template(
        val id: String,
        val label: String,
        val defaultName: String,
    )

    val ALL: List<Template> = listOf(
        Template("empty", "Empty file", "untitled.txt"),
        Template("kotlin", "Kotlin class", "NewClass.kt"),
        Template("java", "Java class", "NewClass.java"),
        Template("python", "Python script", "script.py"),
        Template("html", "HTML page", "index.html"),
        Template("css", "CSS stylesheet", "style.css"),
        Template("json", "JSON document", "data.json"),
        Template("markdown", "Markdown document", "README.md"),
        Template("shell", "Shell script", "script.sh"),
    )

    /** Produces initial text for [templateId] based on [fileName]. */
    fun render(templateId: String, fileName: String): String {
        val stem = fileName.substringBeforeLast('.', fileName).ifBlank { "NewFile" }
        val className = stem.replace(Regex("[^A-Za-z0-9_]"), "_").let { s ->
            if (s.isNotEmpty() && s[0].isDigit()) "_$s" else s
        }.ifEmpty { "NewClass" }
        return when (templateId) {
            "kotlin" -> "class $className {\n\n}\n"
            "java" -> "public class $className {\n    public static void main(String[] args) {\n        \n    }\n}\n"
            "python" -> "def main():\n    pass\n\n\nif __name__ == \"__main__\":\n    main()\n"
            "html" -> "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n  <meta charset=\"UTF-8\">\n  <title>$stem</title>\n</head>\n<body>\n  \n</body>\n</html>\n"
            "css" -> "body {\n  margin: 0;\n  font-family: sans-serif;\n}\n"
            "json" -> "{\n  \n}\n"
            "markdown" -> "# $stem\n\n"
            "shell" -> "#!/usr/bin/env sh\n\nset -e\n"
            else -> ""
        }
    }
}
