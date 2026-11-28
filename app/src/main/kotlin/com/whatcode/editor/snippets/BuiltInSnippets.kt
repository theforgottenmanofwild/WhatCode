package com.whatcode.editor.snippets

/**
 * The snippets that come with the editor, by the name of the language (the one the status bar shows). A snippet's body
 * is written here with the section sign § in place of the dollar sign, so Kotlin does not read it as a template:
 * `§{1:name}` is the tab stop `${1:name}` (see [SnippetBody]). Every prefix has two letters or more.
 */
object BuiltInSnippets {

    private fun s(prefix: String, name: String, body: String) = Snippet(prefix, name, body.replace('§', '$'))

    private val JS = listOf(
        s("log", "console.log", "console.log(§{1:value});"),
        s("fn", "Function", "function §{1:name}(§{2}) {\n\t§0\n}"),
        s("af", "Arrow function in a constant", "const §{1:name} = (§{2}) => {\n\t§0\n};"),
        s("if", "If", "if (§{1:condition}) {\n\t§0\n}"),
        s("ife", "If and else", "if (§{1:condition}) {\n\t§2\n} else {\n\t§0\n}"),
        s("for", "For loop over a number", "for (let i = 0; i < §{1:array}.length; i++) {\n\t§0\n}"),
        s("forof", "For of loop", "for (const §{1:item} of §{2:items}) {\n\t§0\n}"),
        s("try", "Try and catch", "try {\n\t§1\n} catch (§{2:error}) {\n\t§0\n}"),
        s("imp", "Import", "import §{1:name} from '§{2:module}';"),
        s("cls", "Class", "class §{1:Name} {\n\tconstructor(§{2}) {\n\t\t§0\n\t}\n}"),
    )

    private val TS = JS + listOf(
        s("iface", "Interface", "interface §{1:Name} {\n\t§0\n}"),
    )

    private val PYTHON = listOf(
        s("def", "Function", "def §{1:name}(§{2}):\n\t§0"),
        s("cls", "Class", "class §{1:Name}:\n\tdef __init__(self§{2}):\n\t\t§0"),
        s("if", "If", "if §{1:condition}:\n\t§0"),
        s("ife", "If and else", "if §{1:condition}:\n\t§2\nelse:\n\t§0"),
        s("for", "For loop", "for §{1:item} in §{2:items}:\n\t§0"),
        s("while", "While loop", "while §{1:condition}:\n\t§0"),
        s("try", "Try and except", "try:\n\t§1\nexcept §{2:Exception} as §{3:error}:\n\t§0"),
        s("with", "With open", "with open(§{1:path}) as §{2:f}:\n\t§0"),
        s("main", "Run when started", "if __name__ == \"__main__\":\n\t§0"),
    )

    private val KOTLIN = listOf(
        s("fun", "Function", "fun §{1:name}(§{2}) {\n\t§0\n}"),
        s("main", "main", "fun main() {\n\t§0\n}"),
        s("cls", "Class", "class §{1:Name}(§{2}) {\n\t§0\n}"),
        s("dc", "Data class", "data class §{1:Name}(§{2})"),
        s("if", "If", "if (§{1:condition}) {\n\t§0\n}"),
        s("ife", "If and else", "if (§{1:condition}) {\n\t§2\n} else {\n\t§0\n}"),
        s("when", "When", "when (§{1:value}) {\n\t§{2:x} -> §0\n\telse -> {}\n}"),
        s("for", "For loop", "for (§{1:item} in §{2:items}) {\n\t§0\n}"),
        s("try", "Try and catch", "try {\n\t§1\n} catch (e: §{2:Exception}) {\n\t§0\n}"),
    )

    private val JAVA = listOf(
        s("main", "main", "public static void main(String[] args) {\n\t§0\n}"),
        s("cls", "Class", "public class §{1:Name} {\n\t§0\n}"),
        s("if", "If", "if (§{1:condition}) {\n\t§0\n}"),
        s("for", "For loop over a number", "for (int i = 0; i < §{1:n}; i++) {\n\t§0\n}"),
        s("fore", "For each", "for (§{1:Type} §{2:item} : §{3:items}) {\n\t§0\n}"),
        s("sout", "System.out.println", "System.out.println(§{1});"),
        s("try", "Try and catch", "try {\n\t§1\n} catch (§{2:Exception} e) {\n\t§0\n}"),
    )

    private val GO = listOf(
        s("func", "Function", "func §{1:name}(§{2}) §{3} {\n\t§0\n}"),
        s("main", "main", "func main() {\n\t§0\n}"),
        s("iferr", "If err is not nil", "if err != nil {\n\t§0\n}"),
        s("for", "For loop over a number", "for i := 0; i < §{1:n}; i++ {\n\t§0\n}"),
        s("forr", "For range", "for §{1:_}, §{2:v} := range §{3:items} {\n\t§0\n}"),
        s("st", "Struct", "type §{1:Name} struct {\n\t§0\n}"),
    )

    private val RUST = listOf(
        s("fn", "Function", "fn §{1:name}(§{2}) {\n\t§0\n}"),
        s("main", "main", "fn main() {\n\t§0\n}"),
        s("st", "Struct", "struct §{1:Name} {\n\t§0\n}"),
        s("impl", "Impl", "impl §{1:Name} {\n\t§0\n}"),
        s("match", "Match", "match §{1:value} {\n\t§{2:x} => §0,\n\t_ => {}\n}"),
        s("for", "For loop", "for §{1:item} in §{2:items} {\n\t§0\n}"),
        s("pl", "println!", "println!(\"{}\", §{1});"),
    )

    private val C = listOf(
        s("main", "main", "int main(int argc, char *argv[]) {\n\t§0\n\treturn 0;\n}"),
        s("inc", "#include", "#include <§{1:stdio.h}>"),
        s("if", "If", "if (§{1:condition}) {\n\t§0\n}"),
        s("for", "For loop", "for (int i = 0; i < §{1:n}; i++) {\n\t§0\n}"),
        s("st", "Struct", "struct §{1:name} {\n\t§0\n};"),
        s("pf", "printf", "printf(\"§{1}\\n\");"),
    )

    private val CPP = C + listOf(
        s("cls", "Class", "class §{1:Name} {\npublic:\n\t§0\n};"),
        s("cout", "std::cout", "std::cout << §{1} << std::endl;"),
    )

    private val CSHARP = listOf(
        s("main", "Main", "static void Main(string[] args)\n{\n\t§0\n}"),
        s("cls", "Class", "public class §{1:Name}\n{\n\t§0\n}"),
        s("prop", "Property", "public §{1:string} §{2:Name} { get; set; }"),
        s("if", "If", "if (§{1:condition})\n{\n\t§0\n}"),
        s("for", "For loop", "for (int i = 0; i < §{1:n}; i++)\n{\n\t§0\n}"),
        s("foreach", "Foreach", "foreach (var §{1:item} in §{2:items})\n{\n\t§0\n}"),
        s("cw", "Console.WriteLine", "Console.WriteLine(§{1});"),
    )

    private val SWIFT = listOf(
        s("func", "Function", "func §{1:name}(§{2}) -> §{3:Void} {\n\t§0\n}"),
        s("cls", "Class", "class §{1:Name} {\n\t§0\n}"),
        s("if", "If", "if §{1:condition} {\n\t§0\n}"),
        s("for", "For in", "for §{1:item} in §{2:items} {\n\t§0\n}"),
    )

    private val RUBY = listOf(
        s("def", "Method", "def §{1:name}\n\t§0\nend"),
        s("cls", "Class", "class §{1:Name}\n\t§0\nend"),
        s("each", "Each", "§{1:items}.each do |§{2:item}|\n\t§0\nend"),
        s("if", "If", "if §{1:condition}\n\t§0\nend"),
    )

    private val SHELL = listOf(
        s("if", "If", "if [[ §{1:condition} ]]; then\n\t§0\nfi"),
        s("for", "For in", "for §{1:item} in §{2:items}; do\n\t§0\ndone"),
        s("while", "While", "while §{1:condition}; do\n\t§0\ndone"),
        s("case", "Case", "case \\§§{1:value} in\n\t§{2:pattern})\n\t\t§0\n\t\t;;\nesac"),
        s("fn", "Function", "§{1:name}() {\n\t§0\n}"),
    )

    private val HTML = listOf(
        s("html5", "A page", "<!DOCTYPE html>\n<html lang=\"§{1:en}\">\n<head>\n\t<meta charset=\"UTF-8\">\n" +
            "\t<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n\t<title>§{2:Title}</title>\n" +
            "</head>\n<body>\n\t§0\n</body>\n</html>"),
        s("link", "Style sheet link", "<link rel=\"stylesheet\" href=\"§{1:style.css}\">"),
        s("script", "Script", "<script src=\"§{1:app.js}\"></script>"),
        s("anchor", "Link", "<a href=\"§{1:#}\">§{2:text}</a>"),
        s("img", "Image", "<img src=\"§{1}\" alt=\"§{2}\">"),
        s("ul", "List", "<ul>\n\t<li>§0</li>\n</ul>"),
        s("div", "Div", "<div class=\"§{1}\">\n\t§0\n</div>"),
    )

    private val CSS = listOf(
        s("flex", "Flex box", "display: flex;\nalign-items: §{1:center};\njustify-content: §{2:center};"),
        s("grid", "Grid", "display: grid;\ngrid-template-columns: §{1:repeat(2, 1fr)};\ngap: §{2:8px};"),
        s("media", "Media query", "@media (max-width: §{1:600px}) {\n\t§0\n}"),
        s("kf", "Keyframes", "@keyframes §{1:name} {\n\tfrom {\n\t\t§2\n\t}\n\tto {\n\t\t§0\n\t}\n}"),
        s("var", "var()", "var(--§{1:name})"),
    )

    private val MARKDOWN = listOf(
        s("link", "Link", "[§{1:text}](§{2:url})"),
        s("img", "Image", "![§{1:alt}](§{2:path})"),
        s("code", "Code block", "```§{1:lang}\n§0\n```"),
        s("table", "Table", "| §{1:Column} | §{2:Column} |\n| --- | --- |\n| §0 |  |"),
        s("todo", "Task", "- [ ] §0"),
    )

    private val SQL = listOf(
        s("sel", "Select", "SELECT §{1:*}\nFROM §{2:table}\nWHERE §{3:condition};"),
        s("ins", "Insert", "INSERT INTO §{1:table} (§{2:columns})\nVALUES (§{3:values});"),
        s("upd", "Update", "UPDATE §{1:table}\nSET §{2:column} = §{3:value}\nWHERE §{4:condition};"),
        s("del", "Delete", "DELETE FROM §{1:table}\nWHERE §{2:condition};"),
    )

    /** The snippets by language name; languages without any are not listed. */
    val byLanguage: Map<String, List<Snippet>> = mapOf(
        "JavaScript" to JS,
        "TypeScript" to TS,
        "Python" to PYTHON,
        "Kotlin" to KOTLIN,
        "Java" to JAVA,
        "Go" to GO,
        "Rust" to RUST,
        "C" to C,
        "C++" to CPP,
        "C#" to CSHARP,
        "Swift" to SWIFT,
        "Ruby" to RUBY,
        "Shell" to SHELL,
        "HTML" to HTML,
        "CSS" to CSS,
        "SCSS" to CSS,
        "Less" to CSS,
        "Markdown" to MARKDOWN,
        "SQL" to SQL,
    )

    fun forLanguage(langName: String): List<Snippet> = byLanguage[langName] ?: emptyList()
}
