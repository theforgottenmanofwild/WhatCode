package com.whatcodeeditor

import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.regex.Pattern

/** Marker subclass so we can remove only our own highlighting spans. */
class SyntaxSpan(color: Int) : ForegroundColorSpan(color)

/** How a language is read: the family decides which scanner is used. */
enum class Family { PLAIN, CODE, MARKUP, CSS, YAML, INI, MARKDOWN }

/**
 * One language. [name] is what the status bar shows. The word sets say which words are keywords, control
 * keywords (if, return), constants (true, null), types and so on; the other fields describe comments and strings.
 */
class Lang(
    val name: String,
    val family: Family = Family.CODE,
    val keywords: Set<String> = emptySet(),
    val controls: Set<String> = emptySet(),
    val constants: Set<String> = emptySet(),
    val types: Set<String> = emptySet(),
    /** Words that are keywords only where one is expected, such as `from` in `import x from "y"`. */
    val soft: Set<String> = emptySet(),
    /** Words after which comes the name of a function (`fun`, `def`). */
    val defWords: Set<String> = emptySet(),
    /** Words after which comes the name of a type (`class`, `struct`). */
    val typeWords: Set<String> = emptySet(),
    val lineComments: List<String> = emptyList(),
    val blockComments: Boolean = false,
    val quotes: String = "\"'",
    val tripleQuotes: Boolean = false,
    /** Python style prefixes such as r"" and f"". */
    val prefixedStrings: Boolean = false,
    val regexLiterals: Boolean = false,
    /** Capitalised words are types and ALL_CAPS words are constants. */
    val capitalizedTypes: Boolean = false,
    val caseInsensitive: Boolean = false,
    /** A word followed by ( is a function. */
    val calls: Boolean = true,
    /** A string followed by a colon is a key (JSON). */
    val keys: Boolean = false,
    /** Extra patterns with their colours, tried before plain words. */
    val extras: List<Pair<String, Int>> = emptyList(),
    /** Comment markers for languages that have no line comment, such as HTML. */
    val block: Pair<String, String>? = null,
    /** TOML allows a comment after a value. */
    val trailingHash: Boolean = false,
    /** A word followed by a colon, after a { or a comma, is the key of an object (`{ id: 1 }`). */
    val objectKeys: Boolean = false,
) {
    val lineComment: String? get() = lineComments.firstOrNull()

    /** What the comment key puts round a line: the opening and the closing marker (empty for a line comment). */
    val commentPair: Pair<String, String>? get() = lineComment?.let { it to "" } ?: block
}

/** The quote characters that are typed in pairs in this language; none in plain text and Markdown. */
val Lang.pairQuotes: String get() = if (family == Family.PLAIN || family == Family.MARKDOWN) "" else quotes

/** The coloured pieces found in a text: start, end and colour of each, in order and not overlapping. */
class Tokens {
    var data = IntArray(3 * 2048)
    var size = 0

    fun add(start: Int, end: Int, color: Int) {
        if (end <= start) return
        if (size * 3 + 3 > data.size) data = data.copyOf(data.size * 2)
        data[size * 3] = start
        data[size * 3 + 1] = end
        data[size * 3 + 2] = color
        size++
    }
}

object Highlighter {
    private val KEYWORD = 0xFF569CD6.toInt()
    private val CONTROL = 0xFFC586C0.toInt()
    private val TYPE = 0xFF4EC9B0.toInt()
    private val FUNCTION = 0xFFDCDCAA.toInt()
    private val CONSTANT = 0xFF4FC1FF.toInt()
    private val PROPERTY = 0xFF9CDCFE.toInt()
    private val STRING = 0xFFCE9178.toInt()
    private val NUMBER = 0xFFB5CEA8.toInt()
    private val COMMENT = 0xFF6A9955.toInt()
    private val REGEX = 0xFFD16969.toInt()
    private val SELECTOR = 0xFFD7BA7D.toInt()

    // Kinds of piece that need a decision in code. Real colours are large negative numbers, so these never clash.
    private const val S_WORD = 1
    private const val S_STRING = 2
    private const val S_REGEX = 3
    private const val S_KEYLEAD = 4
    private const val S_TYPELEAD = 5
    private const val S_QSTR = 6
    private const val S_TAGOPEN = 7
    private const val S_TAGEND = 8
    private const val S_TSTR = 9
    private const val S_ATTR = 10
    private const val S_ENTITY = 11
    private const val S_AT = 12
    private const val S_PROP = 13
    private const val S_HASH = 14
    private const val S_CLASS = 15
    private const val S_PSEUDO = 16
    private const val S_PUNCT = 17
    private const val S_IDENT = 18
    private const val S_URL = 19

    /** Files longer than this are shown without highlighting. */
    const val MAX_CHARS = 1_000_000

    /** Texts up to this long are coloured at once; longer ones are read on a background thread. */
    private const val SYNC_CHARS = 20_000

    private fun words(s: String): Set<String> = s.split(' ').filter { it.isNotEmpty() }.toSet()

    // ------------------------------------------------------------------ the languages

    private const val ANNOTATION = """@[A-Za-z_][\w.]*"""

    private val JS_KW = words("async await class const debugger delete export extends function import in instanceof let new static super this typeof var void with yield")
    private val JS_CTRL = words("break case catch continue default do else finally for if return switch throw try while")
    private val JS_SOFT = words("as from get of set")
    private val JS_CONST = words("true false null undefined NaN Infinity")

    private val JS = Lang(
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
    private val CSS = Lang("CSS", Family.CSS, block = "/*" to "*/")
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

    // ------------------------------------------------------------------ colouring the editor

    private val worker by lazy {
        Executors.newSingleThreadExecutor { r -> Thread(r, "highlighter").apply { isDaemon = true } }
    }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    /** Counts the requests, so that the answer to an old request is thrown away. Only the main thread changes it. */
    @Volatile
    private var generation = 0

    /**
     * Colours [e] for [lang]. Call it from the main thread. Short texts are coloured at once; long ones are read
     * on a background thread and coloured when that is done, unless the text changed in the meantime.
     */
    fun apply(e: Editable, lang: Lang) {
        val gen = ++generation
        if (lang.family == Family.PLAIN || e.length > MAX_CHARS) {
            clear(e)
            return
        }
        val text = e.toString()
        if (text.length <= SYNC_CHARS) {
            paint(e, tokenize(text, lang))
            return
        }
        worker.execute {
            val tokens = tokenize(text, lang)
            mainHandler.post {
                if (gen == generation && e.length == text.length && e.toString() == text) paint(e, tokens)
            }
        }
    }

    private fun clear(e: Editable) {
        for (s in e.getSpans(0, e.length, SyntaxSpan::class.java)) e.removeSpan(s)
    }

    private fun paint(e: Editable, t: Tokens) {
        clear(e)
        val len = e.length
        for (i in 0 until t.size) {
            val b = i * 3
            val end = minOf(t.data[b + 1], len)
            if (end > t.data[b]) e.setSpan(SyntaxSpan(t.data[b + 2]), t.data[b], end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    // ------------------------------------------------------------------ finding the pieces

    private class Compiled(val pattern: Pattern, val kinds: IntArray)

    private val cache = ConcurrentHashMap<String, Compiled>()

    private fun compile(specs: List<Pair<String, Int>>): Compiled =
        Compiled(Pattern.compile(specs.joinToString("|") { "(${it.first})" }), IntArray(specs.size) { specs[it].second })

    /** Reads [text] and returns every piece that is to be coloured. Safe to call from any thread. */
    fun tokenize(text: String, lang: Lang): Tokens {
        val out = Tokens()
        try {
            scan(text, 0, text.length, lang, out)
        } catch (_: Exception) {
            // keep what was found so far
        } catch (_: StackOverflowError) {
            // a pattern met a text it could not handle: keep what was found so far
        }
        return out
    }

    private fun scan(text: String, from: Int, to: Int, lang: Lang, out: Tokens) {
        when (lang.family) {
            Family.PLAIN -> {}
            Family.CODE -> scanCode(text, from, to, lang, out)
            Family.MARKUP -> scanMarkup(text, from, to, out)
            Family.CSS -> scanCss(text, from, to, lang, out)
            Family.YAML, Family.INI, Family.MARKDOWN -> scanRules(text, from, to, lang, out)
        }
    }

    /**
     * A quoted string that stops at the quote, at the end of the line, or at the end of the text.
     * The repeats are possessive (`*+`): nothing they give back could ever let the end match, so the result is the
     * same, but Java no longer recurses once per escape, which overflowed a phone's thread stack on long lines.
     */
    private fun quoted(q: Char): String = """$q[^$q\\\n]*+(?:\\[\s\S][^$q\\\n]*+)*+(?:$q|(?=\n)|\z)"""

    private const val NUM =
        """0[xX][0-9a-fA-F_]+[lLuUnN]*|0[bB][01_]+[lLuUnN]*|\d[\d_]*(?:\.\d[\d_]*)?(?:[eE][+-]?\d+)?[fFdDlLuUn]*"""

    private const val WORD = """[A-Za-z_$\u0080-\uFFFF][\w$\u0080-\uFFFF]*"""

    private fun codeRules(lang: Lang): Compiled = cache.getOrPut("code:" + lang.name) {
        val specs = ArrayList<Pair<String, Int>>()
        if (lang.lineComments.isNotEmpty()) {
            specs.add("(?:" + lang.lineComments.joinToString("|") { Pattern.quote(it) } + ")[^\\n]*" to COMMENT)
        }
        if (lang.blockComments) specs.add("""/\*[\s\S]*?(?:\*/|\z)""" to COMMENT)

        val strings = ArrayList<String>()
        if (lang.tripleQuotes) {
            for (q in listOf('"', '\'')) {
                if (q in lang.quotes) {
                    val t = q.toString().repeat(3)
                    strings.add("$t[\\s\\S]*?(?:$t|\\z)")
                }
            }
        }
        for (q in lang.quotes) {
            strings.add(if (q == '`') """`[^`\\]*+(?:\\[\s\S][^`\\]*+)*+(?:`|\z)""" else quoted(q))
        }
        if (strings.isNotEmpty()) {
            val all = strings.joinToString("|")
            specs.add((if (lang.prefixedStrings) "(?:[rRbBfFuU]{1,2})?(?:$all)" else all) to S_STRING)
        }
        if (lang.regexLiterals) {
            specs.add("""/(?![*/])(?:\\.|\[(?:\\.|[^\]\\\n])*\]|[^/\\\n\[])+/[a-z]*""" to S_REGEX)
        }
        specs.add(NUM to NUMBER)
        specs.addAll(lang.extras)
        specs.add(WORD to S_WORD)
        compile(specs)
    }

    private val REGEX_PRE_WORDS = words("return typeof instanceof case do else yield await void delete throw in of new")

    /** True if a / at [s] can start a regular expression, going by what comes before it. */
    private fun regexAllowed(text: String, s: Int, from: Int): Boolean {
        var p = s - 1
        while (p >= from && text[p].isWhitespace()) p--
        if (p < from) return true
        val c = text[p]
        if (c == ')' || c == ']' || c == '}' || c == '"' || c == '\'' || c == '`') return false
        if (c.isLetterOrDigit() || c == '_' || c == '$') {
            var b = p
            while (b > from && (text[b - 1].isLetterOrDigit() || text[b - 1] == '_' || text[b - 1] == '$')) b--
            return text.substring(b, p + 1) in REGEX_PRE_WORDS
        }
        if (c == '+' || c == '-') return p > from && text[p - 1] != c
        return true
    }

    private fun nextChar(text: String, from: Int, to: Int): Char {
        var k = from
        while (k < to && (text[k] == ' ' || text[k] == '\t')) k++
        return if (k < to) text[k] else '\u0000'
    }

    /** The last character before [s] that is not white space, or a NUL if there is none. */
    private fun prevSignificant(text: String, s: Int, from: Int): Char {
        var p = s - 1
        while (p >= from && text[p].isWhitespace()) p--
        return if (p >= from) text[p] else '\u0000'
    }

    private fun onlySpaces(text: String, a: Int, b: Int): Boolean {
        for (i in a until b) if (!text[i].isWhitespace()) return false
        return true
    }

    private fun softOk(c: Char): Boolean = c.isLetter() || c == '_' || c == '"' || c == '\'' || c == '`' || c == '{' || c == '*'

    private fun isConstantName(w: String): Boolean {
        if (w.length < 2) return false
        var letter = false
        for (ch in w) {
            if (ch.isLetter()) {
                if (!ch.isUpperCase()) return false
                letter = true
            } else if (!ch.isDigit() && ch != '_') {
                return false
            }
        }
        return letter
    }

    private fun scanCode(text: String, from: Int, to: Int, lang: Lang, out: Tokens) {
        val rules = codeRules(lang)
        val n = rules.kinds.size
        val m = rules.pattern.matcher(text)
        m.region(from, to)
        var prevWord = ""
        var prevEnd = -1
        while (m.find()) {
            var g = 1
            while (g <= n && m.start(g) < 0) g++
            if (g > n) continue
            val s = m.start()
            val e = m.end()
            when (val kind = rules.kinds[g - 1]) {
                S_STRING -> {
                    val color = if (lang.keys && nextChar(text, e, to) == ':') PROPERTY else STRING
                    out.add(s, e, color)
                }
                S_REGEX -> {
                    if (regexAllowed(text, s, from)) {
                        out.add(s, e, REGEX)
                    } else {
                        m.region(s + 1, to) // it was a division sign
                    }
                }
                S_WORD -> {
                    val word = text.substring(s, e)
                    val key = if (lang.caseInsensitive) word.lowercase(Locale.ROOT) else word
                    val afterDot = s > from && text[s - 1] == '.' && !(s >= from + 3 && text[s - 2] == '.' && text[s - 3] == '.')
                    val next = nextChar(text, e, to)
                    val prev = if (prevEnd >= 0 && onlySpaces(text, prevEnd, s)) prevWord else ""
                    var color = 0
                    if (!afterDot) {
                        color = when {
                            key in lang.controls -> CONTROL
                            key in lang.keywords || key in lang.constants -> KEYWORD
                            key in lang.soft && softOk(next) -> KEYWORD
                            else -> 0
                        }
                    }
                    if (color == 0 && key in lang.types) color = TYPE
                    if (color == 0) {
                        val cap = lang.capitalizedTypes && word[0].isUpperCase()
                        color = when {
                            lang.objectKeys && !afterDot && next == ':' && prevSignificant(text, s, from).let { it == '{' || it == ',' } -> PROPERTY
                            prev in lang.defWords && (next == '(' || next == '<' || next == '[') -> FUNCTION
                            prev in lang.typeWords -> TYPE
                            afterDot && lang.calls && next == '(' -> FUNCTION
                            lang.capitalizedTypes && prev == "new" -> TYPE
                            cap -> if (isConstantName(word)) CONSTANT else TYPE
                            lang.capitalizedTypes && !afterDot && isConstantName(word) -> CONSTANT
                            lang.calls && next == '(' -> FUNCTION
                            else -> 0
                        }
                    }
                    if (color != 0) out.add(s, e, color)
                    prevWord = key
                    prevEnd = e
                }
                else -> out.add(s, e, kind)
            }
        }
    }

    // ---- HTML, XML, SVG

    private val markupRules: Compiled by lazy {
        compile(
            listOf(
                """<!--[\s\S]*?(?:-->|\z)""" to COMMENT,
                """<!\[CDATA\[[\s\S]*?(?:\]\]>|\z)""" to STRING,
                """<![A-Za-z][^>]*>|<\?[\s\S]*?(?:\?>|\z)""" to KEYWORD,
                """</?[A-Za-z][\w:.-]*""" to S_TAGOPEN,
                """/?>""" to S_TAGEND,
                """&(?:[A-Za-z][A-Za-z0-9]*|#[0-9]+|#[xX][0-9a-fA-F]+);""" to S_ENTITY,
                "\"[^\"]*\"|'[^']*'" to S_TSTR,
                """[A-Za-z_:@][\w:.@\-\[\]()*#]*""" to S_ATTR,
            )
        )
    }

    private fun scanMarkup(text: String, from: Int, to: Int, out: Tokens) {
        val rules = markupRules
        val n = rules.kinds.size
        val m = rules.pattern.matcher(text)
        m.region(from, to)
        var inTag = false
        var raw: String? = null
        while (m.find()) {
            var g = 1
            while (g <= n && m.start(g) < 0) g++
            if (g > n) continue
            val s = m.start()
            val e = m.end()
            when (val kind = rules.kinds[g - 1]) {
                S_TAGOPEN -> {
                    out.add(s, e, KEYWORD)
                    inTag = true
                    val closing = text[s + 1] == '/'
                    val name = text.substring(if (closing) s + 2 else s + 1, e).lowercase(Locale.ROOT)
                    raw = if (!closing && (name == "script" || name == "style")) name else null
                }
                S_TAGEND -> if (inTag) {
                    out.add(s, e, KEYWORD)
                    inTag = false
                    val rt = raw
                    raw = null
                    if (rt != null && text[s] != '/') {
                        // The text of a script or style element is another language, up to its closing tag.
                        val close = text.indexOf("</$rt", e, ignoreCase = true)
                        val end = if (close < 0) to else minOf(close, to)
                        if (end > e) {
                            if (rt == "script") scanCode(text, e, end, JS, out) else scanCss(text, e, end, CSS, out)
                        }
                        m.region(end, to)
                    }
                }
                S_TSTR -> if (inTag) out.add(s, e, STRING) else m.region(s + 1, to)
                S_ATTR -> if (inTag) out.add(s, e, PROPERTY)
                S_ENTITY -> if (!inTag) out.add(s, e, CONSTANT)
                else -> out.add(s, e, kind)
            }
        }
    }

    // ---- CSS, SCSS, Less

    private fun cssRules(lang: Lang): Compiled = cache.getOrPut("css:" + lang.name) {
        val specs = ArrayList<Pair<String, Int>>()
        specs.add("""/\*[\s\S]*?(?:\*/|\z)""" to COMMENT)
        if (lang.lineComments.isNotEmpty()) specs.add("""//[^\n]*""" to COMMENT)
        specs.add("""url\((?:"[^"\n]*"|'[^'\n]*'|[^)\n]*)\)""" to S_URL)
        specs.add(quoted('"') + "|" + quoted('\'') to STRING)
        specs.add("""@[A-Za-z-]+""" to S_AT)
        specs.add("""-{0,2}[A-Za-z][\w-]*(?=\s*:\s*[^\s{][^;{}]*[;}])""" to S_PROP)
        specs.add("""--[\w-]+""" to PROPERTY)
        specs.add("""[$][\w-]+""" to PROPERTY)
        specs.add("""#[\w-]+""" to S_HASH)
        specs.add("""(?<![\w-])[+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?(?:%|[A-Za-z]+)?""" to NUMBER)
        specs.add("""\.[A-Za-z_-][\w-]*""" to S_CLASS)
        specs.add("""::?[A-Za-z-]+(?:\([^)\n]*\))?""" to S_PSEUDO)
        specs.add("""!\s*important""" to KEYWORD)
        specs.add("""[{};]""" to S_PUNCT)
        specs.add("""-?[A-Za-z_][\w-]*""" to S_IDENT)
        compile(specs)
    }

    private fun isHexColor(text: String, a: Int, b: Int): Boolean {
        val len = b - a
        if (len != 3 && len != 4 && len != 6 && len != 8) return false
        for (i in a until b) {
            val c = text[i]
            if (!(c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F')) return false
        }
        return true
    }

    private fun scanCss(text: String, from: Int, to: Int, lang: Lang, out: Tokens) {
        val rules = cssRules(lang)
        val n = rules.kinds.size
        val m = rules.pattern.matcher(text)
        m.region(from, to)
        var inDecl = false // after a property name, up to the ; or }
        var inAt = false // after an @rule, up to the { or ;
        while (m.find()) {
            var g = 1
            while (g <= n && m.start(g) < 0) g++
            if (g > n) continue
            val s = m.start()
            val e = m.end()
            when (val kind = rules.kinds[g - 1]) {
                S_PROP -> {
                    out.add(s, e, PROPERTY)
                    inDecl = true
                }
                S_AT -> {
                    out.add(s, e, CONTROL)
                    inAt = true
                }
                S_URL -> {
                    out.add(s, s + 3, FUNCTION)
                    out.add(s + 4, e - 1, STRING)
                }
                S_HASH -> {
                    if (!inDecl) out.add(s, e, SELECTOR) else if (isHexColor(text, s + 1, e)) out.add(s, e, NUMBER)
                }
                S_CLASS -> if (!inDecl) out.add(s, e, SELECTOR)
                S_PSEUDO -> if (!inDecl && !inAt) out.add(s, e, SELECTOR)
                S_PUNCT -> {
                    inDecl = false
                    inAt = false
                }
                S_IDENT -> {
                    if (nextChar(text, e, to) == '(') out.add(s, e, FUNCTION) else if (!inDecl && !inAt) out.add(s, e, SELECTOR)
                }
                else -> out.add(s, e, kind)
            }
        }
    }

    // ---- YAML, TOML and INI, Markdown

    private fun listRules(lang: Lang): Compiled = cache.getOrPut("list:" + lang.name) {
        val specs = ArrayList<Pair<String, Int>>()
        when (lang.family) {
            Family.YAML -> {
                specs.add("""(?<![^\s])#[^\n]*""" to COMMENT)
                specs.add("""(?m:^(?:---|\.\.\.)(?=\s|\z))""" to KEYWORD)
                specs.add(
                    """(?m:^[ \t]*(?:-[ \t]+)*)(?:"[^"\n]*"|'[^'\n]*'|[^\s#:'"\-\[\]{},&*!|>%@`][^:#\n]*?)(?=[ \t]*:(?:[ \t]|\r|\n|\z))""" to S_KEYLEAD
                )
                specs.add(quoted('"') + "|" + quoted('\'') to S_QSTR)
                specs.add("""(?<![\w])[&*][A-Za-z0-9_.\-]+""" to TYPE)
                specs.add("""(?<![\w.\-])[+-]?(?:0x[0-9a-fA-F]+|\d[\d_]*(?:\.\d+)?(?:[eE][+-]?\d+)?)(?![\w.\-])""" to NUMBER)
                specs.add("""(?<![\w.\-])(?:[Tt]rue|[Ff]alse|TRUE|FALSE|[Nn]ull|NULL|[Yy]es|[Nn]o|YES|NO|[Oo]n|[Oo]ff|~)(?![\w.\-])""" to KEYWORD)
            }
            Family.INI -> {
                specs.add("""(?m:^[ \t]*[#;][^\n]*)""" to COMMENT)
                if (lang.trailingHash) specs.add("""(?<=\s)#[^\n]*""" to COMMENT)
                specs.add("""(?m:^[ \t]*)\[\[?[^\]\n]*\]\]?""" to S_TYPELEAD)
                specs.add("""(?m:^[ \t]*)[A-Za-z_][\w.\-]*(?=[ \t]*[=:])""" to S_KEYLEAD)
                if (lang.trailingHash) specs.add("\"\"\"[\\s\\S]*?(?:\"\"\"|\\z)|'''[\\s\\S]*?(?:'''|\\z)" to STRING)
                specs.add(quoted('"') + "|" + """'[^'\n]*(?:'|(?=\n)|\z)""" to S_QSTR)
                specs.add("""(?<![\w.\-])[+-]?(?:0x[0-9a-fA-F_]+|\d[\d_]*(?:\.\d+)?(?:[eE][+-]?\d+)?)(?![\w.\-])""" to NUMBER)
                specs.add("""(?<![\w.\-])(?:true|false)(?![\w.\-])""" to KEYWORD)
            }
            else -> {
                specs.add("""(?m:^[ \t]*(?:```|~~~)[^\n]*\n)[\s\S]*?(?:(?m:^[ \t]*(?:```|~~~)[^\n]*$)|\z)""" to STRING)
                specs.add("""<!--[\s\S]*?(?:-->|\z)""" to COMMENT)
                specs.add("""(?m:^#{1,6}[ \t].*$)""" to KEYWORD)
                specs.add("""(?m:^[ \t]*(?:-{3,}|\*{3,}|_{3,})[ \t]*$)""" to CONTROL)
                specs.add("""(?m:^[ \t]*>[^\n]*)""" to COMMENT)
                specs.add("""(?m:^[ \t]*(?:[-*+]|\d+[.)])(?=[ \t]))""" to CONTROL)
                specs.add("""`[^`\n]+`""" to STRING)
                specs.add("""!?\[[^\]\n]*\](?:\([^)\n]*\)|\[[^\]\n]*\])?""" to CONSTANT)
                specs.add("""<https?://[^>\s]+>|https?://[^\s)>\]]+""" to CONSTANT)
                specs.add("""\*\*[^*\n]+\*\*|__[^_\n]+__""" to TYPE)
                specs.add("""(?<![*\w])\*[^*\s][^*\n]*\*(?![*\w])|(?<![_\w])_[^_\s][^_\n]*_(?![_\w])""" to TYPE)
            }
        }
        compile(specs)
    }

    /** Skips the spaces and the "- " of list items at the start of a key. */
    private fun skipLead(text: String, s: Int, e: Int): Int {
        var i = s
        while (i < e && (text[i] == ' ' || text[i] == '\t')) i++
        while (i + 1 < e && text[i] == '-' && (text[i + 1] == ' ' || text[i + 1] == '\t')) {
            i += 2
            while (i < e && (text[i] == ' ' || text[i] == '\t')) i++
        }
        return i
    }

    /** A quote starts a string only after one of : - [ , { = or at the start of a line, so a word like don't is left alone. */
    private fun stringStartOk(text: String, s: Int, from: Int): Boolean {
        var p = s - 1
        while (p >= from && (text[p] == ' ' || text[p] == '\t')) p--
        if (p < from) return true
        val c = text[p]
        return c == '\n' || c == '\r' || c == ':' || c == '-' || c == '[' || c == ',' || c == '{' || c == '='
    }

    private fun scanRules(text: String, from: Int, to: Int, lang: Lang, out: Tokens) {
        val rules = listRules(lang)
        val n = rules.kinds.size
        val m = rules.pattern.matcher(text)
        m.region(from, to)
        while (m.find()) {
            var g = 1
            while (g <= n && m.start(g) < 0) g++
            if (g > n) continue
            val s = m.start()
            val e = m.end()
            when (val kind = rules.kinds[g - 1]) {
                S_KEYLEAD -> out.add(skipLead(text, s, e), e, PROPERTY)
                S_TYPELEAD -> out.add(skipLead(text, s, e), e, TYPE)
                S_QSTR -> if (stringStartOk(text, s, from)) out.add(s, e, STRING) else m.region(s + 1, to)
                else -> out.add(s, e, kind)
            }
        }
    }
}
