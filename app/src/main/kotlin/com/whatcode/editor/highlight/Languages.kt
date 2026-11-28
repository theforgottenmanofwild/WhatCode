package com.whatcode.editor.highlight

import com.whatcode.editor.Family
import com.whatcode.editor.Lang
import java.util.Locale

/** Every language the editor knows, how a file name picks one, and the word lists and patterns that describe it. */
internal object Languages {
    private const val ANNOTATION = """@[A-Za-z_][\w.]*"""

    private val JS_KW = words("async await class const debugger delete export extends function import in instanceof let new static super this typeof var void with yield")
    private val JS_CTRL = words("break case catch continue default do else finally for if return switch throw try while")
    private val JS_SOFT = words("as from get of set")
    private val JS_CONST = words("true false null undefined NaN Infinity")

    val JS = Lang(
        "JavaScript", keywords = JS_KW, controls = JS_CTRL, constants = JS_CONST, soft = JS_SOFT,
        defWords = words("function"), typeWords = words("class"),
        lineComments = listOf("//"), blockComments = true, quotes = "\"'`", regexLiterals = true, capitalizedTypes = true,
        objectKeys = true,
    )
    private val TS = Lang(
        "TypeScript",
        keywords = JS_KW + words("abstract declare enum implements interface namespace private protected public readonly override keyof infer module satisfies"),
        controls = JS_CTRL, constants = JS_CONST, soft = JS_SOFT + words("type is asserts"),
        types = words("string number boolean any unknown never void object symbol bigint"),
        defWords = words("function"), typeWords = words("class interface enum type"),
        lineComments = listOf("//"), blockComments = true, quotes = "\"'`", regexLiterals = true, capitalizedTypes = true,
        objectKeys = true,
    )
    private val KOTLIN = Lang(
        "Kotlin",
        keywords = words("abstract annotation as class const constructor do enum external final fun import in inline interface internal is lateinit object override package private protected public sealed super this typealias val var vararg"),
        controls = words("break catch continue else finally for if return throw try when while"),
        constants = words("true false null"),
        soft = words("by companion crossinline data get init inner noinline open out reified set suspend tailrec value where field infix actual expect"),
        defWords = words("fun"), typeWords = words("class interface object enum typealias"),
        lineComments = listOf("//"), blockComments = true, tripleQuotes = true, capitalizedTypes = true,
        extras = listOf("""@[A-Za-z_][\w.]*(?::[A-Za-z_]\w*)?""" to FUNCTION),
    )
    private val JAVA = Lang(
        "Java",
        keywords = words("abstract assert boolean byte char class const double enum extends final float implements import instanceof int interface long native new package private protected public short static strictfp super synchronized this transient void volatile var record sealed permits"),
        controls = words("break case catch continue default do else finally for goto if return switch throw throws try while yield"),
        constants = words("true false null"),
        typeWords = words("class interface enum record"),
        lineComments = listOf("//"), blockComments = true, tripleQuotes = true, capitalizedTypes = true,
        extras = listOf(ANNOTATION to FUNCTION),
    )
    private val GO = Lang(
        "Go",
        keywords = words("chan const func import interface map package struct type var"),
        controls = words("break case continue default defer else fallthrough for go goto if range return select switch"),
        constants = words("true false nil iota"),
        types = words("string int int8 int16 int32 int64 uint uint8 uint16 uint32 uint64 uintptr byte rune float32 float64 complex64 complex128 bool error any comparable"),
        defWords = words("func"), typeWords = words("type"),
        lineComments = listOf("//"), blockComments = true, quotes = "\"'`",
    )
    private val RUST = Lang(
        "Rust",
        keywords = words("as async await const crate dyn enum extern fn impl let mod move mut pub ref self Self static struct super trait type unsafe use where"),
        controls = words("break continue else for if in loop match return while yield"),
        constants = words("true false"),
        types = words("i8 i16 i32 i64 i128 isize u8 u16 u32 u64 u128 usize f32 f64 bool char str"),
        defWords = words("fn"), typeWords = words("struct enum trait type impl union"),
        lineComments = listOf("//"), blockComments = true, quotes = "\"", capitalizedTypes = true,
        extras = listOf(
            """b?r#{0,3}"[\s\S]*?"#{0,3}""" to STRING,
            """b?'(?:\\(?:x[0-9a-fA-F]{2}|u\{[0-9a-fA-F_]+\}|.)|[^'\\\n])'""" to STRING,
            """'[A-Za-z_]\w*""" to TYPE,
            """#!?\[[^\]\n]*\]""" to FUNCTION,
            """[A-Za-z_]\w*!(?=[(\[{])""" to FUNCTION,
        ),
    )
    private val C_LANG = Lang(
        "C",
        keywords = words("auto const enum extern inline register restrict sizeof static struct typedef union volatile void char short int long float double signed unsigned _Bool bool"),
        controls = words("break case continue default do else for goto if return switch while"),
        constants = words("true false NULL"),
        types = words("size_t ssize_t ptrdiff_t uint8_t uint16_t uint32_t uint64_t int8_t int16_t int32_t int64_t uintptr_t intptr_t FILE"),
        typeWords = words("struct enum union"),
        lineComments = listOf("//"), blockComments = true, capitalizedTypes = true,
        extras = listOf("""(?m:^[ \t]*#[ \t]*[A-Za-z]+)""" to CONTROL),
    )
    private val CPP = Lang(
        "C++",
        keywords = words("auto const enum extern inline register restrict sizeof static struct typedef union volatile void char short int long float double signed unsigned bool class namespace template typename using virtual override final public private protected friend this new delete operator explicit mutable constexpr consteval constinit noexcept static_assert decltype asm export concept requires"),
        controls = words("break case catch continue default do else for goto if return switch throw try while co_await co_return co_yield"),
        constants = words("true false NULL nullptr"),
        types = words("size_t ssize_t uint8_t uint16_t uint32_t uint64_t int8_t int16_t int32_t int64_t string wstring vector map set list array deque unordered_map unordered_set unique_ptr shared_ptr optional variant pair tuple"),
        typeWords = words("class struct enum union"),
        lineComments = listOf("//"), blockComments = true, capitalizedTypes = true,
        extras = listOf("""(?m:^[ \t]*#[ \t]*[A-Za-z]+)""" to CONTROL),
    )
    private val CSHARP = Lang(
        "C#",
        keywords = words("abstract as base bool byte char checked class const decimal delegate double enum event explicit extern fixed float implicit in int interface internal is lock long namespace new object operator out override params private protected public readonly ref sbyte sealed short sizeof stackalloc static string struct this typeof uint ulong unchecked unsafe ushort using var virtual void volatile async await"),
        controls = words("break case catch continue default do else finally for foreach goto if return switch throw try while yield"),
        constants = words("true false null"),
        soft = words("get set value partial init record add remove global"),
        typeWords = words("class struct interface enum record namespace"),
        lineComments = listOf("//"), blockComments = true, capitalizedTypes = true,
        extras = listOf("""(?m:^[ \t]*#[ \t]*[A-Za-z]+)""" to CONTROL),
    )
    private val SWIFT = Lang(
        "Swift",
        keywords = words("associatedtype class deinit enum extension fileprivate func import init inout internal let open operator private protocol public rethrows static struct subscript typealias var as is super self Self throws throw try defer async await actor some any where in nonisolated mutating override convenience required lazy final weak"),
        controls = words("break case catch continue default do else fallthrough for guard if repeat return switch while yield"),
        constants = words("true false nil"),
        defWords = words("func"), typeWords = words("class struct enum protocol extension actor typealias"),
        lineComments = listOf("//"), blockComments = true, tripleQuotes = true, capitalizedTypes = true,
        extras = listOf("""@[A-Za-z_]\w*""" to FUNCTION, """#[A-Za-z]+""" to CONTROL),
    )
    private val DART = Lang(
        "Dart",
        keywords = words("abstract as async await base class const covariant deferred dynamic enum export extends extension external factory final function implements import in interface is late library mixin new operator part required rethrow sealed static super this typedef var void with"),
        controls = words("assert break case catch continue default do else finally for if return switch throw try while yield"),
        constants = words("true false null"),
        soft = words("get set show hide on of"),
        typeWords = words("class mixin enum extension typedef"),
        lineComments = listOf("//"), blockComments = true, tripleQuotes = true, capitalizedTypes = true,
        extras = listOf("""@[A-Za-z_]\w*""" to FUNCTION),
    )
    private val PHP = Lang(
        "PHP",
        keywords = words("abstract and array as callable class clone const echo empty enddeclare extends final function global implements include include_once instanceof insteadof interface isset list namespace new or print private protected public require require_once static trait unset use var xor fn match readonly enum"),
        controls = words("break case catch continue declare default do else elseif endfor endforeach endif endswitch endwhile finally for foreach goto if return switch throw try while yield"),
        constants = words("true false null"),
        defWords = words("function"), typeWords = words("class interface trait enum"),
        lineComments = listOf("//", "#"), blockComments = true, capitalizedTypes = true, caseInsensitive = true,
        extras = listOf("""<\?(?:php|=)?|\?>""" to CONTROL, """[$][A-Za-z_]\w*""" to PROPERTY),
    )
    private val SCALA = Lang(
        "Scala",
        keywords = words("abstract class def enum export extends extension final forSome given implicit import lazy new object override package private protected sealed super this trait type val var with opaque inline using"),
        controls = words("case catch do else finally for if match return then throw try while yield"),
        constants = words("true false null"),
        defWords = words("def"), typeWords = words("class object trait enum type given"),
        lineComments = listOf("//"), blockComments = true, tripleQuotes = true, capitalizedTypes = true,
        extras = listOf(ANNOTATION to FUNCTION),
    )
    private val PYTHON = Lang(
        "Python",
        keywords = words("and as assert async await class def del from global import in is lambda nonlocal not or pass raise with yield self cls"),
        controls = words("break continue elif else except finally for if return try while"),
        constants = words("True False None"),
        soft = words("match case type"),
        types = words("int str float bool list dict set tuple bytes bytearray object range complex frozenset memoryview slice"),
        defWords = words("def"), typeWords = words("class"),
        lineComments = listOf("#"), tripleQuotes = true, prefixedStrings = true, capitalizedTypes = true,
        extras = listOf(ANNOTATION to FUNCTION),
    )
    private val RUBY = Lang(
        "Ruby",
        keywords = words("alias and class def in module not or self super undef BEGIN END defined"),
        controls = words("begin break case do else elsif end ensure for if next redo rescue retry return then unless until when while yield"),
        constants = words("true false nil"),
        defWords = words("def"), typeWords = words("class module"),
        lineComments = listOf("#"), quotes = "\"'`", capitalizedTypes = true,
        extras = listOf(
            """(?m:^=begin[\s\S]*?(?:^=end[^\n]*|\z))""" to COMMENT,
            """(?<![:\w]):[A-Za-z_]\w*[?!]?""" to CONSTANT,
            """@@?[A-Za-z_]\w*""" to PROPERTY,
        ),
    )
    private val SHELL = Lang(
        "Shell",
        keywords = words("echo cd export local return exit read set unset source alias unalias eval exec trap shift test printf declare readonly"),
        controls = words("if then else elif fi for while until do done case esac in function select"),
        constants = words("true false"),
        lineComments = listOf("#"), quotes = "\"'`", calls = false,
        extras = listOf("""[$](?:\{[^}\n]*\}|[A-Za-z_]\w*|[0-9@#?*!$-])""" to PROPERTY),
    )
    private val MAKEFILE = Lang(
        "Makefile",
        keywords = words("ifeq ifneq ifdef ifndef else endif include define endef export override unexport vpath"),
        lineComments = listOf("#"), calls = false,
        extras = listOf(
            """[$]\([^)\n]*\)|[$]\{[^}\n]*\}|[$][@<^?*%+]""" to PROPERTY,
            """(?m:^[A-Za-z0-9_./%-]+(?=[ \t]*:(?!=)))""" to FUNCTION,
        ),
    )
    private val DOCKERFILE = Lang(
        "Dockerfile",
        lineComments = listOf("#"), calls = false,
        extras = listOf(
            """(?mi:^[ \t]*(?:FROM|RUN|CMD|LABEL|MAINTAINER|EXPOSE|ENV|ADD|COPY|ENTRYPOINT|VOLUME|USER|WORKDIR|ARG|ONBUILD|STOPSIGNAL|HEALTHCHECK|SHELL)\b)""" to KEYWORD,
            """(?i:(?<=\s)AS(?=\s))""" to KEYWORD,
            """[$](?:\{[^}\n]*\}|[A-Za-z_]\w*)""" to PROPERTY,
        ),
    )
    private val GRADLE = Lang(
        "Gradle",
        keywords = words("def class interface enum import package new in as instanceof static final abstract extends implements void int long boolean double float byte char short this super"),
        controls = words("if else for while switch case default break continue return try catch finally throw"),
        constants = words("true false null"),
        lineComments = listOf("//"), blockComments = true, tripleQuotes = true, capitalizedTypes = true,
    )
    private val GO_MOD = Lang(
        "Go module",
        keywords = words("module go require replace exclude retract toolchain godebug use"),
        lineComments = listOf("//"), quotes = "\"`", calls = false,
    )
    private val SQL = Lang(
        "SQL",
        keywords = words("select from where and or not in is like between join inner left right full outer cross on group by order having limit offset insert into values update set delete create alter drop table index view database schema primary key foreign references unique default check constraint as distinct union all exists case when then else end begin commit rollback transaction with recursive returning asc desc truncate grant revoke if replace function procedure trigger using natural over partition window"),
        constants = words("true false null"),
        types = words("int integer bigint smallint tinyint varchar char text boolean bool date time timestamp datetime decimal numeric float double real serial uuid json jsonb blob"),
        lineComments = listOf("--"), blockComments = true, quotes = "\"'`", caseInsensitive = true,
    )
    private val JSON = Lang(
        "JSON", constants = words("true false null"), quotes = "\"", calls = false, keys = true,
    )
    private val JSONC = Lang(
        "JSON with comments", constants = words("true false null"), quotes = "\"", calls = false, keys = true,
        lineComments = listOf("//"), blockComments = true,
    )
    private val HTML = Lang("HTML", Family.MARKUP, block = "<!--" to "-->")
    private val XML = Lang("XML", Family.MARKUP, block = "<!--" to "-->")
    private val SVG = Lang("SVG", Family.MARKUP, block = "<!--" to "-->")
    val CSS = Lang("CSS", Family.CSS, block = "/*" to "*/")
    private val SCSS = Lang("SCSS", Family.CSS, lineComments = listOf("//"))
    private val LESS = Lang("Less", Family.CSS, lineComments = listOf("//"))
    private val YAML = Lang("YAML", Family.YAML, lineComments = listOf("#"))
    private val TOML = Lang("TOML", Family.INI, lineComments = listOf("#"), trailingHash = true)
    private val INI = Lang("INI", Family.INI, lineComments = listOf(";"))
    private val PROPERTIES = Lang("Properties", Family.INI, lineComments = listOf("#"))
    private val MARKDOWN = Lang("Markdown", Family.MARKDOWN, block = "<!--" to "-->")
    private val PLAIN = Lang("Text", Family.PLAIN)

    private val byExt: Map<String, Lang> = HashMap<String, Lang>().apply {
        fun add(exts: String, lang: Lang) {
            for (e in exts.split(" ")) if (e.isNotEmpty()) put(e, lang)
        }
        add("js jsx mjs cjs", JS)
        add("ts tsx mts cts", TS)
        add("kt kts", KOTLIN)
        add("java", JAVA)
        add("go", GO)
        add("rs", RUST)
        add("c h", C_LANG)
        add("cpp cc cxx hpp hh hxx", CPP)
        add("cs", CSHARP)
        add("swift", SWIFT)
        add("dart", DART)
        add("php", PHP)
        add("scala sc", SCALA)
        add("py pyw", PYTHON)
        add("rb rake gemspec", RUBY)
        add("sh bash zsh ksh", SHELL)
        add("mk", MAKEFILE)
        add("gradle", GRADLE)
        add("sql", SQL)
        add("json", JSON)
        add("jsonc", JSONC)
        add("html htm xhtml", HTML)
        add("xml xsd xsl plist", XML)
        add("svg", SVG)
        add("css", CSS)
        add("scss", SCSS)
        add("less", LESS)
        add("yml yaml", YAML)
        add("toml", TOML)
        add("ini cfg conf editorconfig", INI)
        add("properties", PROPERTIES)
        add("md markdown", MARKDOWN)
    }

    private val byName: Map<String, Lang> = mapOf(
        "dockerfile" to DOCKERFILE,
        "makefile" to MAKEFILE,
        "gnumakefile" to MAKEFILE,
        "go.mod" to GO_MOD,
        "go.work" to GO_MOD,
    )

    /** Every language the editor knows, by name, for lists such as the key layouts. Text comes last. */
    val languages: List<Lang> by lazy {
        (byExt.values + byName.values).distinctBy { it.name }.sortedBy { it.name.lowercase(Locale.ROOT) } + PLAIN
    }

    fun languageFor(fileName: String?): Lang {
        val lower = fileName?.lowercase(Locale.ROOT) ?: return PLAIN
        byName[lower]?.let { return it }
        val ext = if ('.' in lower) lower.substringAfterLast('.') else ""
        return byExt[ext] ?: PLAIN
    }
}
