package com.whatcode.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class JsonFormatTest {

    private fun pretty(t: String, width: Int = 2): String =
        JsonFormat.edit(t, 0, 0, pretty = true, indentWidth = width)!!.insert

    private fun minify(t: String): String = JsonFormat.edit(t, 0, 0, pretty = false, indentWidth = 2)!!.insert

    private fun refusal(t: String, a: Int = 0, b: Int = 0, pretty: Boolean = true): String {
        try {
            JsonFormat.edit(t, a, b, pretty, 2)
        } catch (e: TextToolFailure) {
            return e.message!!
        }
        fail("expected the text to be refused: $t")
        return ""
    }

    // ------------------------------------------------------------------ pretty

    @Test
    fun anObjectGetsOneNamePerLine() {
        assertEquals("{\n  \"a\": 1,\n  \"b\": [\n    true,\n    null\n  ]\n}", pretty("{\"a\":1,\"b\":[true,null]}"))
    }

    @Test
    fun aTabOrEightSpacesCanBeTheIndent() {
        assertEquals("{\n\t\"a\": [\n\t\t1\n\t]\n}", pretty("{\"a\":[1]}", width = 0))
        assertEquals("[\n        1\n]", pretty("[1]", width = 8))
    }

    @Test
    fun emptyContainersStayOnOneLine() {
        assertEquals("{\n  \"a\": {},\n  \"b\": []\n}", pretty("{ \"a\" : { } , \"b\" : [ \n ] }"))
        assertEquals("[]", pretty("[ ]"))
    }

    @Test
    fun nestedArraysGetOneLevelEach() {
        assertEquals("[\n  [\n    1,\n    [\n      2\n    ]\n  ]\n]", pretty("[[1,[2]]]"))
    }

    @Test
    fun aLoneValueIsLeftAsIt() {
        assertNull(JsonFormat.edit("42", 0, 0, pretty = true, indentWidth = 2))
        assertNull(JsonFormat.edit("\"hi\"", 0, 0, pretty = true, indentWidth = 2))
        assertNull(JsonFormat.edit("null", 0, 0, pretty = false, indentWidth = 2))
    }

    @Test
    fun textThatIsAlreadyFormattedGivesNoEdit() {
        val t = "{\n  \"a\": [\n    1,\n    2\n  ]\n}"
        assertNull(JsonFormat.edit(t, 0, 0, pretty = true, indentWidth = 2))
        assertNull(JsonFormat.edit("{\"a\":[1,2]}", 0, 0, pretty = false, indentWidth = 2))
    }

    @Test
    fun namesStringsAndNumbersAreCopiedExactly() {
        val t = "{\"z\":1.50,\"a\":1E3,\"s\":\"\\u00e9\\n\\\"{[,]}\",\"n\":-0.0e-5,\"u\":\"\uD83D\uDE00\"}"
        val out = pretty(t)
        assertTrue(out.contains("\"z\": 1.50"))
        assertTrue(out.contains("\"a\": 1E3"))
        assertTrue(out.contains("\"s\": \"\\u00e9\\n\\\"{[,]}\""))
        assertTrue(out.contains("\"n\": -0.0e-5"))
        assertTrue(out.contains("\"u\": \"\uD83D\uDE00\""))
        assertTrue(out.indexOf("\"z\"") < out.indexOf("\"a\""))
        assertEquals(t, minify(out))
    }

    // ------------------------------------------------------------------ minify

    @Test
    fun minifyTakesOutEveryWhiteSpace() {
        assertEquals("{\"a\":[1,2,{\"b\":null}],\"c\":\"x y\"}", minify("{ \"a\" : [ 1 , 2 ,\r\n {\"b\" : null} ] ,\n\t\"c\" : \"x y\" }"))
    }

    @Test
    fun whiteSpaceInsideAStringStays() {
        assertEquals("[\"  a \\t b  \"]", minify("[ \"  a \\t b  \" ]"))
    }

    // ------------------------------------------------------------------ what is replaced

    @Test
    fun whiteSpaceAroundTheJsonIsNotTouched() {
        val t = "\n\n  {\"a\":1}  \n\n"
        val e = JsonFormat.edit(t, 0, 0, pretty = true, indentWidth = 2)!!
        assertEquals(t.indexOf('{'), e.start)
        assertEquals(t.lastIndexOf('}') + 1, e.end)
        assertEquals("{\n    \"a\": 1\n  }", e.insert) // the lines of the result start where the first one does
        assertEquals(e.start, e.selStart)
        assertEquals(e.start, e.selEnd)
    }

    @Test
    fun aFileEndingInANewlineKeepsIt() {
        val t = "[1,2]\n"
        val e = JsonFormat.edit(t, 0, 0, pretty = true, indentWidth = 2)!!
        assertEquals("[\n  1,\n  2\n]\n", t.substring(0, e.start) + e.insert + t.substring(e.end))
    }

    @Test
    fun theLineBreaksAreTheOnesTheTextAlreadyUses() {
        val crlf = "{\"a\":1}\r\n"
        val e = JsonFormat.edit(crlf, 0, 0, pretty = true, indentWidth = 2)!!
        assertEquals("{\r\n  \"a\": 1\r\n}", e.insert)
        val cr = JsonFormat.edit("[1]\r[2]\r", 0, 3, pretty = true, indentWidth = 2)!!
        assertEquals("[\r  1\r]", cr.insert)
        assertEquals("[\n  1\n]", JsonFormat.edit("[1]", 0, 0, pretty = true, indentWidth = 2)!!.insert)
    }

    @Test
    fun aSelectionIsFormattedAndStaysSelected() {
        val t = "x = {\"a\":[1]};\nrest"
        val from = t.indexOf('{')
        val to = t.indexOf('}') + 1
        val e = JsonFormat.edit(t, from, to, pretty = true, indentWidth = 2)!!
        assertEquals(from, e.start)
        assertEquals(to, e.end)
        assertEquals("{\n  \"a\": [\n    1\n  ]\n}", e.insert)
        assertEquals(from, e.selStart)
        assertEquals(from + e.insert.length, e.selEnd)
    }

    @Test
    fun aSelectionKeepsTheIndentationOfItsLine() {
        val t = "    \"data\": {\"a\":[1]},\n    \"next\": 2"
        val from = t.indexOf('{')
        val to = t.indexOf('}') + 1
        val e = JsonFormat.edit(t, from, to, pretty = true, indentWidth = 2)!!
        assertEquals("{\n      \"a\": [\n        1\n      ]\n    }", e.insert)
    }

    @Test
    fun aBackwardsSelectionWorksToo() {
        val t = "a [1] b"
        val e = JsonFormat.edit(t, t.indexOf(']') + 1, t.indexOf('['), pretty = true, indentWidth = 2)!!
        assertEquals("[\n  1\n]", e.insert)
    }

    @Test
    fun aSelectionOfOnlyWhiteSpaceHasNoJson() {
        assertEquals("There is no JSON here to format", refusal("a   b", 1, 4))
        assertEquals("There is no JSON here to format", refusal(""))
        assertEquals("There is no JSON here to format", refusal(" \n\t "))
    }

    // ------------------------------------------------------------------ what is refused

    @Test
    fun problemsAreReportedWithTheirLineAndColumn() {
        assertEquals("Not valid JSON: Unexpected ']', at line 1, column 4", refusal("[1,]"))
        assertEquals("Not valid JSON: Expected ',' or '}', at line 3, column 3", refusal("{\n  \"a\": 1\n  \"b\": 2\n}"))
        assertEquals("Not valid JSON: Expected ':' after the name, at line 1, column 6", refusal("{\"a\" 1}"))
        assertEquals("Not valid JSON: Expected a name in double quotes, at line 1, column 2", refusal("{a: 1}"))
        assertEquals("Not valid JSON: The JSON ends too soon, at line 1, column 10", refusal("{\"a\": [1,\n"))
        assertEquals("Not valid JSON: There is more text after the JSON, at line 1, column 7", refusal("[1,2] 3"))
    }

    @Test
    fun lineNumbersCountEveryKindOfLineBreakAndTheWholeText() {
        assertEquals("Not valid JSON: Unexpected '}', at line 3, column 1", refusal("[\r\n1,\r\n}"))
        assertEquals("Not valid JSON: Unexpected '}', at line 3, column 1", refusal("[\r1,\r}"))
        val t = "a\nb\n[1,]"
        assertEquals("Not valid JSON: Unexpected ']', at line 3, column 4", refusal(t, 4, t.length))
    }

    @Test
    fun whatIsNotStrictJsonIsRefused() {
        for (bad in listOf(
            "{'a': 1}", "[1, 2,]", "{\"a\": 1,}", "// c\n[1]", "[1] /* c */", "{\"a\": undefined}", "[NaN]", "[Infinity]",
            "[01]", "[1.]", "[.5]", "[+1]", "[1e]", "[0x10]", "[1 2]", "[\"a\" \"b\"]", "{\"a\"}", "{\"a\":}", "[,1]", "{,}",
            "[tru]", "[nul]", "[truee]", "]", "}", ":", "{\"a\": 1}}", "[[1]", "{\"a\": [}", "[1}", "{\"a\": 1]",
        )) {
            for (pretty in listOf(true, false)) assertTrue(bad, refusal(bad, pretty = pretty).startsWith("Not valid JSON: "))
        }
    }

    @Test
    fun badStringsAreRefused() {
        for (bad in listOf("[\"abc]", "[\"a\nb\"]", "[\"a\tb\"]", "[\"\\x\"]", "[\"\\u12\"]", "[\"\\u12G4\"]", "[\"\\")) {
            assertTrue(bad, refusal(bad).startsWith("Not valid JSON: The string"))
        }
        // a name that is not closed
        assertTrue(refusal("{\"a: 1}").startsWith("Not valid JSON: The string"))
    }

    @Test
    fun aBadEscapeIsReportedWhereItIs() {
        assertEquals("Not valid JSON: The string is not closed, or holds a bad escape or a raw line break, at line 1, column 4", refusal("[\"a\\q\"]"))
    }

    @Test
    fun aRefusedTextIsNeverChanged() {
        // The edit throws before it returns anything, so the editor is never asked to change.
        val t = "{\"a\": 1,}"
        assertTrue(runCatching { JsonFormat.edit(t, 0, 0, pretty = true, indentWidth = 2) }.isFailure)
    }

    @Test
    fun deepNestingIsRefusedButTheLimitItselfWorks() {
        val ok = "[".repeat(JsonFormat.MAX_DEPTH) + "]".repeat(JsonFormat.MAX_DEPTH)
        assertNull(JsonFormat.edit(ok, 0, 0, pretty = false, indentWidth = 2))
        assertEquals(ok, minify(pretty(ok)))
        assertEquals(2 * (JsonFormat.MAX_DEPTH - 1), pretty(ok).count { it == '\n' })
        val tooDeep = "[".repeat(JsonFormat.MAX_DEPTH + 1) + "]".repeat(JsonFormat.MAX_DEPTH + 1)
        assertTrue(refusal(tooDeep).contains("nested more than ${JsonFormat.MAX_DEPTH} levels"))
    }

    // ------------------------------------------------------------------ size

    @Test
    fun aHugeSingleLineRoundTrips() {
        val items = (1..60_000).joinToString(",") { "{\"id\":$it,\"name\":\"item $it\",\"tags\":[\"a\",\"b\"],\"ok\":${it % 2 == 0}}" }
        val one = "[$items]"
        assertTrue(one.length > 2_000_000)
        val long = pretty(one)
        assertTrue(long.count { it == '\n' } > 500_000)
        assertEquals(one, minify(long))
        assertNull(JsonFormat.edit(long, 0, 0, pretty = true, indentWidth = 2))
    }

    @Test
    fun theEditCanBeAppliedToTheText() {
        val t = "  [1,\n{\"a\":2}]  "
        val e = JsonFormat.edit(t, 0, 0, pretty = true, indentWidth = 2)!!
        assertEquals("  [\n    1,\n    {\n      \"a\": 2\n    }\n  ]  ", t.substring(0, e.start) + e.insert + t.substring(e.end))
    }
}
