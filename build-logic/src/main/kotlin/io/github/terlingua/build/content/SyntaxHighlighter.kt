package io.github.terlingua.build.content

/**
 * Small build-time lexer producing coarse token kinds (kw, str, com, num, lit, fn, type, ann,
 * op, var, tag, attr). Plain text has an empty kind. It favours robustness over precision:
 * any input yields tokens whose concatenation equals the original code.
 */
internal object SyntaxHighlighter {

    data class Token(val kind: String, val text: String)

    private class Lang(
        val keywords: Set<String>,
        val literals: Set<String> = setOf("true", "false", "null"),
        val lineComments: List<String> = listOf("//"),
        val blockComment: Pair<String, String>? = "/*" to "*/",
        val quotes: String = "\"'",
        val tripleQuotes: Boolean = false,
        val annotations: Boolean = false,
        val variables: Boolean = false,
        val caseInsensitive: Boolean = false,
    ) {
        val keywordSet: Set<String> = if (caseInsensitive) keywords.mapTo(HashSet()) { it.lowercase() } else keywords
        val dashedIdentifiers: Boolean = "@media" in keywords
    }

    private fun words(s: String) = s.trim().split(Regex("\\s+")).toSet()

    private val C_LIKE = words("if else for while do switch case default break continue return goto sizeof typedef struct union enum static const volatile extern inline void char short int long float double signed unsigned auto register")
    private val CPP = C_LIKE + words("class namespace template typename public private protected virtual override final new delete this operator using try catch throw noexcept constexpr nullptr bool auto friend explicit mutable static_cast dynamic_cast reinterpret_cast const_cast concept requires co_await co_return co_yield")
    private val JAVA = words("abstract assert boolean break byte case catch char class const continue default do double else enum extends final finally float for goto if implements import instanceof int interface long native new package private protected public return short static strictfp super switch synchronized this throw throws transient try void volatile while var record sealed permits yield non-sealed")
    private val KOTLIN = words("as break class continue do else for fun if in interface is object package return super this throw try typealias typeof val var when while by catch constructor delegate dynamic field file finally get import init param property receiver set setparam where actual abstract annotation companion const crossinline data enum expect external final infix inline inner internal lateinit noinline open operator out override private protected public reified sealed suspend tailrec vararg value context")
    private val CSHARP = words("abstract as base bool break byte case catch char checked class const continue decimal default delegate do double else enum event explicit extern finally fixed float for foreach goto if implicit in int interface internal is lock long namespace new object operator out override params private protected public readonly ref return sbyte sealed short sizeof stackalloc static string struct switch this throw try typeof uint ulong unchecked unsafe ushort using virtual void volatile while add alias ascending async await by descending dynamic equals from get global group init into join let nameof notnull on orderby partial record remove required select set unmanaged value var when where with yield file scoped field extension")
    private val JS = words("break case catch class const continue debugger default delete do else export extends finally for function if import in instanceof let new return super switch this throw try typeof var void while with yield async await of static get set from as")
    private val TS = JS + words("interface type enum implements namespace declare abstract private protected public readonly keyof infer is unknown never any string number boolean symbol bigint object satisfies override module")
    private val PYTHON = words("and as assert async await break class continue def del elif else except finally for from global if import in is lambda nonlocal not or pass raise return try while with yield match case type")
    private val GO = words("break case chan const continue default defer else fallthrough for func go goto if import interface map package range return select struct switch type var")
    private val RUST = words("as async await break const continue crate dyn else enum extern fn for if impl in let loop match mod move mut pub ref return self Self static struct super trait type unsafe use where while")
    private val SWIFT = words("associatedtype class deinit enum extension fileprivate func import init inout internal let open operator private protocol public rethrows static struct subscript typealias var break case continue default defer do else fallthrough for guard if in repeat return switch where while as catch is rethrows throw throws try async await actor some any")
    private val SQL = words("select from where and or not insert into values update set delete create table drop alter add column primary key foreign references index unique join left right inner outer full cross on as group by order having limit offset union all distinct case when then else end is null like in between exists with recursive view trigger begin commit rollback transaction default check constraint asc desc returning merge using over partition window cast coalesce count sum avg min max int integer text varchar char boolean date timestamp real numeric")
    private val SHELL = words("if then else elif fi for in do done while until case esac function return local export readonly unset shift exit break continue source alias echo cd set trap eval exec")
    private val POWERSHELL = words("begin break catch class continue data do dynamicparam else elseif end exit filter finally for foreach from function if in param process return switch throw trap try until using var while workflow")
    private val RUBY = words("alias and begin break case class def defined do else elsif end ensure false for if in module next nil not or redo rescue retry return self super then true undef unless until when while yield require attr_accessor")
    private val PHP = words("abstract and array as break callable case catch class clone const continue declare default do echo else elseif empty enddeclare endfor endforeach endif endswitch endwhile extends final finally fn for foreach function global goto if implements include instanceof insteadof interface isset list match namespace new or print private protected public readonly require return static switch throw trait try unset use var while yield")
    private val DART = words("abstract as assert async await break case catch class const continue covariant default deferred do dynamic else enum export extends extension external factory final finally for get if implements import in interface is late library mixin new on operator part required rethrow return sealed set show static super switch sync this throw try typedef var void when while with yield")
    private val SCALA = words("abstract case catch class def do else extends final finally for forSome given if implicit import lazy match new object override package private protected return sealed super then this throw trait try type using val var while with yield enum export")

    private val languages: Map<String, Lang> = buildMap {
        val kotlin = Lang(KOTLIN, tripleQuotes = true, annotations = true)
        val java = Lang(JAVA, annotations = true)
        val csharp = Lang(CSHARP)
        val js = Lang(JS, quotes = "\"'`")
        val ts = Lang(TS, quotes = "\"'`", annotations = true)
        val python = Lang(PYTHON, literals = setOf("True", "False", "None"), lineComments = listOf("#"), blockComment = null, tripleQuotes = true, annotations = true)
        val shell = Lang(SHELL, literals = emptySet(), lineComments = listOf("#"), blockComment = null, variables = true)
        val cpp = Lang(CPP + setOf("true", "false"))
        listOf("kotlin", "kt", "kts").forEach { put(it, kotlin) }
        listOf("java").forEach { put(it, java) }
        listOf("csharp", "cs", "c#", "razor", "cshtml").forEach { put(it, csharp) }
        listOf("javascript", "js", "jsx", "mjs", "cjs").forEach { put(it, js) }
        listOf("typescript", "ts", "tsx").forEach { put(it, ts) }
        listOf("python", "py").forEach { put(it, python) }
        listOf("bash", "sh", "shell", "zsh", "console", "shell-session").forEach { put(it, shell) }
        listOf("c", "h").forEach { put(it, Lang(C_LIKE)) }
        listOf("cpp", "c++", "hpp", "cc", "cxx").forEach { put(it, cpp) }
        put("go", Lang(GO, literals = setOf("true", "false", "nil", "iota"), quotes = "\"'`"))
        listOf("rust", "rs").forEach { put(it, Lang(RUST, literals = setOf("true", "false", "None", "Some", "Ok", "Err"), annotations = false)) }
        put("swift", Lang(SWIFT, literals = setOf("true", "false", "nil"), tripleQuotes = true, annotations = true))
        listOf("sql", "sqlite", "postgresql", "psql", "tsql", "mysql").forEach {
            put(it, Lang(SQL, literals = setOf("true", "false", "null"), lineComments = listOf("--"), quotes = "'\"", caseInsensitive = true))
        }
        listOf("powershell", "ps1", "pwsh", "ps").forEach {
            put(it, Lang(POWERSHELL, literals = setOf("\$true", "\$false", "\$null"), lineComments = listOf("#"), blockComment = "<#" to "#>", variables = true, caseInsensitive = true))
        }
        listOf("ruby", "rb").forEach { put(it, Lang(RUBY, literals = setOf("true", "false", "nil"), lineComments = listOf("#"), blockComment = null)) }
        put("php", Lang(PHP, lineComments = listOf("//", "#"), variables = true, caseInsensitive = true))
        put("dart", Lang(DART, tripleQuotes = true, annotations = true))
        put("scala", Lang(SCALA, tripleQuotes = true, annotations = true))
        listOf("groovy", "gradle").forEach { put(it, Lang(JAVA + words("def as in trait"), tripleQuotes = true, annotations = true)) }
        listOf("dockerfile", "docker").forEach {
            put(it, Lang(words("FROM RUN CMD LABEL EXPOSE ENV ADD COPY ENTRYPOINT VOLUME USER WORKDIR ARG ONBUILD STOPSIGNAL HEALTHCHECK SHELL AS"), literals = emptySet(), lineComments = listOf("#"), blockComment = null, variables = true, caseInsensitive = true))
        }
        listOf("css", "scss", "less").forEach { put(it, Lang(words("@media @import @keyframes @font-face !important"), literals = emptySet(), lineComments = if (it == "css") emptyList() else listOf("//"))) }
        listOf("r").forEach { put(it, Lang(words("if else repeat while function for in next break"), literals = setOf("TRUE", "FALSE", "NULL", "NA", "Inf", "NaN"), lineComments = listOf("#"), blockComment = null)) }
        listOf("toml", "ini", "properties", "env").forEach { put(it, Lang(emptySet(), lineComments = listOf("#", ";"), blockComment = null)) }
    }

    private val markup = setOf("xml", "html", "htm", "svg", "xaml", "xhtml", "vue", "plist", "csproj", "props", "targets", "axml")
    private val json = setOf("json", "jsonc", "json5")
    private val yaml = setOf("yaml", "yml")

    fun highlight(code: String, language: String): List<Token> {
        val lang = language.lowercase().trim()
        val tokens = when {
            lang in markup -> markupTokens(code)
            lang in json -> jsonTokens(code)
            lang in yaml -> yamlTokens(code)
            else -> languages[lang]?.let { genericTokens(code, it) } ?: listOf(Token("", code))
        }
        return merge(tokens)
    }

    private fun merge(tokens: List<Token>): List<Token> {
        val out = ArrayList<Token>(tokens.size)
        for (t in tokens) {
            if (t.text.isEmpty()) continue
            val last = out.lastOrNull()
            if (last != null && last.kind == t.kind) out[out.lastIndex] = Token(t.kind, last.text + t.text) else out += t
        }
        return out
    }

    private fun isIdentStart(c: Char) = c.isLetter() || c == '_' || c == '$'
    private fun isIdentPart(c: Char) = c.isLetterOrDigit() || c == '_' || c == '$'
    private const val OPERATORS = "+-*/%=<>!&|^~?:"

    private fun genericTokens(code: String, lang: Lang): List<Token> {
        val out = mutableListOf<Token>()
        val n = code.length
        var i = 0
        fun emit(kind: String, end: Int) {
            out += Token(kind, code.substring(i, end))
            i = end
        }
        while (i < n) {
            val c = code[i]
            val block = lang.blockComment
            when {
                block != null && code.startsWith(block.first, i) -> {
                    val end = code.indexOf(block.second, i + block.first.length)
                    emit("com", if (end < 0) n else end + block.second.length)
                }
                lang.lineComments.any { code.startsWith(it, i) } -> {
                    val end = code.indexOf('\n', i)
                    emit("com", if (end < 0) n else end)
                }
                lang.tripleQuotes && (code.startsWith("\"\"\"", i) || code.startsWith("'''", i)) -> {
                    val q = code.substring(i, i + 3)
                    val end = code.indexOf(q, i + 3)
                    emit("str", if (end < 0) n else end + 3)
                }
                c in lang.quotes -> emit("str", stringEnd(code, i, c, multiline = c == '`'))
                c.isDigit() || (c == '.' && i + 1 < n && code[i + 1].isDigit()) -> {
                    var j = i + 1
                    while (j < n && (code[j].isLetterOrDigit() || code[j] == '_' || (code[j] == '.' && j + 1 < n && code[j + 1].isDigit()))) j++
                    emit("num", j)
                }
                lang.annotations && c == '@' && i + 1 < n && isIdentStart(code[i + 1]) -> {
                    var j = i + 1
                    while (j < n && (isIdentPart(code[j]) || code[j] == '.')) j++
                    emit("ann", j)
                }
                lang.variables && c == '$' && i + 1 < n && (isIdentStart(code[i + 1]) || code[i + 1] == '{' || code[i + 1].isDigit()) -> {
                    var j = i + 1
                    if (code[j] == '{') {
                        val end = code.indexOf('}', j)
                        j = if (end < 0) n else end + 1
                    } else {
                        while (j < n && isIdentPart(code[j]) && code[j] != '$') j++
                    }
                    val word = code.substring(i, j)
                    emit(if (lang.literals.any { it.equals(word, ignoreCase = true) }) "lit" else "var", j)
                }
                isIdentStart(c) || (c == '@' && lang.keywords.any { it.startsWith("@") }) || (c == '!' && code.startsWith("!important", i)) -> {
                    var j = i + 1
                    while (j < n && (isIdentPart(code[j]) || (code[j] == '-' && lang.dashedIdentifiers))) j++
                    val word = code.substring(i, j)
                    val key = if (lang.caseInsensitive) word.lowercase() else word
                    var k = j
                    while (k < n && (code[k] == ' ' || code[k] == '\t')) k++
                    val kind = when {
                        key in lang.keywordSet -> "kw"
                        lang.literals.any { if (lang.caseInsensitive) it.equals(word, true) else it == word } -> "lit"
                        k < n && code[k] == '(' -> "fn"
                        word.first().isUpperCase() && word.any { it.isLowerCase() } -> "type"
                        else -> ""
                    }
                    emit(kind, j)
                }
                c in OPERATORS -> {
                    var j = i + 1
                    while (j < n && code[j] in OPERATORS) j++
                    emit("op", j)
                }
                else -> {
                    var j = i + 1
                    while (j < n && code[j].isWhitespace() && c.isWhitespace()) j++
                    emit("", j)
                }
            }
        }
        return out
    }

    private fun stringEnd(code: String, start: Int, quote: Char, multiline: Boolean): Int {
        var j = start + 1
        while (j < code.length) {
            val ch = code[j]
            if (ch == '\\') {
                j += 2
                continue
            }
            if (ch == quote) return j + 1
            if (ch == '\n' && !multiline) return j
            j++
        }
        return code.length
    }

    private val MARKUP = Regex("""<!--[\s\S]*?(?:-->|$)|<!\[CDATA\[[\s\S]*?(?:]]>|$)|</?[A-Za-z][\w:.-]*|/?>|"[^"]*"|'[^']*'|[A-Za-z_:][\w:.-]*(?==)|=""")

    private fun markupTokens(code: String): List<Token> {
        val out = mutableListOf<Token>()
        var last = 0
        var inTag = false
        for (m in MARKUP.findAll(code)) {
            val text = m.value
            val kind = when {
                text.startsWith("<!--") -> "com"
                text.startsWith("<![CDATA[") -> "str"
                text.startsWith("<") -> { inTag = true; "tag" }
                text == ">" || text == "/>" -> if (inTag) { inTag = false; "tag" } else null
                !inTag -> null
                text == "=" -> "op"
                text.startsWith("\"") || text.startsWith("'") -> "str"
                else -> "attr"
            } ?: continue
            if (m.range.first > last) out += Token("", code.substring(last, m.range.first))
            out += Token(kind, text)
            last = m.range.last + 1
        }
        if (last < code.length) out += Token("", code.substring(last))
        return out
    }

    private val JSON_TOKEN = Regex(""""(?:\\.|[^"\\])*"(\s*:)?|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?|\b(?:true|false|null)\b|//[^\n]*""")

    private fun jsonTokens(code: String): List<Token> {
        val out = mutableListOf<Token>()
        var last = 0
        for (m in JSON_TOKEN.findAll(code)) {
            if (m.range.first > last) out += Token("", code.substring(last, m.range.first))
            val v = m.value
            when {
                v.startsWith("//") -> out += Token("com", v)
                v.startsWith("\"") -> {
                    val colon = m.groups[1]?.value
                    if (colon != null) {
                        out += Token("attr", v.removeSuffix(colon))
                        out += Token("", colon)
                    } else {
                        out += Token("str", v)
                    }
                }
                v.first().isLetter() -> out += Token("lit", v)
                else -> out += Token("num", v)
            }
            last = m.range.last + 1
        }
        if (last < code.length) out += Token("", code.substring(last))
        return out
    }

    private val YAML_LINE = Regex("""^(\s*-?\s*)([^\s#'"][^:#]*?|"[^"]*"|'[^']*')(:)(?=\s|$)""")
    private val YAML_VALUE = Regex("""#.*$|"(?:\\.|[^"\\])*"|'[^']*'|\b(?:true|false|null|yes|no|on|off)\b|-?\b\d+(?:\.\d+)?\b|[|>]-?$""")

    private fun yamlTokens(code: String): List<Token> {
        val out = mutableListOf<Token>()
        code.split("\n").forEachIndexed { index, line ->
            if (index > 0) out += Token("", "\n")
            var rest = line
            if (line.trimStart().startsWith("#")) {
                out += Token("com", line)
                return@forEachIndexed
            }
            YAML_LINE.find(line)?.let { m ->
                out += Token("", m.groupValues[1])
                out += Token("attr", m.groupValues[2])
                out += Token("op", m.groupValues[3])
                rest = line.substring(m.range.last + 1)
            }
            var last = 0
            for (m in YAML_VALUE.findAll(rest)) {
                if (m.range.first > last) out += Token("", rest.substring(last, m.range.first))
                val v = m.value
                val kind = when {
                    v.startsWith("#") -> "com"
                    v.startsWith("\"") || v.startsWith("'") -> "str"
                    v.first().isDigit() || v.first() == '-' -> "num"
                    v.first() == '|' || v.first() == '>' -> "op"
                    else -> "lit"
                }
                out += Token(kind, v)
                last = m.range.last + 1
            }
            if (last < rest.length) out += Token("", rest.substring(last))
        }
        return out
    }
}
