package com.whatcode.editor

import com.whatcode.editor.highlight.Languages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CancellationException

class OutlineTest {

    private fun lang(name: String): Lang = Highlighter.languageFor(name)

    private fun names(text: String, file: String) = Outline.find(text, lang(file)).map { it.name }

    private fun scope(text: String, file: String, at: Int): List<String> {
        val syms = Outline.find(text, lang(file))
        return Outline.scopeAt(syms, text, at).map { it.name }
    }

    @Test
    fun kotlinFunctionsAndTypesAreFoundWithModifiersGenericsAndReceivers() {
        val src = """
            package a
            // fun notThis() is a comment
            class Foo {
                private suspend fun run(x: Int) {}
                fun <T> list(): List<T> = x
                fun String.shout() = this
            }
            object Bar
        """.trimIndent()
        assertEquals(listOf("Foo", "run", "list", "String.shout", "Bar"), names(src, "a.kt"))
    }

    @Test
    fun pythonAndGoAndRust() {
        assertEquals(listOf("A", "m", "top"), names("class A(B):\n    async def m(self):\n        pass\ndef top():\n    pass\n".replace("async def m", "def m"), "a.py"))
        assertEquals(listOf("Name", "Plain"), names("func (r *T) Name() {}\nfunc Plain() {}\n", "a.go"))
        assertEquals(listOf("go", "Shape"), names("pub fn go() {}\nstruct Shape {}\n", "a.rs"))
    }

    @Test
    fun theKindAndTheOffsetOfTheNameAreRight() {
        val src = "x\n  fun hello() {}\nclass K"
        val syms = Outline.find(src, lang("a.kt"))
        assertEquals(Outline.Kind.FUNCTION, syms[0].kind)
        assertEquals(2, syms[0].line)
        assertEquals(src.indexOf("hello"), syms[0].offset)
        assertEquals(2, syms[0].indent)
        assertEquals(Outline.Kind.TYPE, syms[1].kind)
        assertEquals(3, syms[1].line)
    }

    @Test
    fun aWordInsideTextOrAnAssignmentIsNotADefinition() {
        val src = "val s = \"fun fake() {}\"\n * the class Foo is nice\nx = function() {}\n"
        assertTrue(names(src, "a.kt").isEmpty())
        assertTrue(names("x = function() {}\n", "a.js").isEmpty())
        assertEquals(listOf("real"), names("export default async function real() {}\n", "a.js"))
    }

    @Test
    fun markdownHeadingsSkipCodeFences() {
        val md = "# One\ntext\n```\n# not a heading\n```\n## Two ##\n###### Six\n#NoSpace\n"
        val syms = Outline.find(md, lang("a.md"))
        assertEquals(listOf("One", "Two", "Six"), syms.map { it.name })
        assertEquals(listOf(0, 2, 10), syms.map { it.indent })
        assertEquals(Outline.Kind.HEADING, syms[0].kind)
    }

    @Test
    fun cssRulesYamlKeysAndIniSectionsAndHtmlHeadings() {
        assertEquals(listOf("body", ".a > .b", "@media (max-width: 1px)"), names("body {\n  color: red;\n}\n.a   >  .b {\n}\n@media (max-width: 1px) {\n  p { }\n}\n", "a.css"))
        assertEquals(listOf("name", "items"), names("name: x\nitems:\n  - a\n  key: v\n", "a.yml"))
        assertEquals(listOf("main", "other"), names("[main]\nk=v\n[other]\n", "a.ini"))
        assertEquals(listOf("Title", "Sub bold"), names("<h1 class=\"x\">Title</h1>\n<p></p>\n<h2>Sub <b>bold</b></h2>\n", "a.html"))
    }

    @Test
    fun plainTextAndAnEmptyTextHaveNoOutline() {
        assertTrue(names("fun a() {}\n", "a.txt").isEmpty())
        assertTrue(names("", "a.kt").isEmpty())
        assertTrue(names("\n\n\n", "a.kt").isEmpty())
    }

    @Test
    fun crlfAndAFinalLineWithoutABreakAreRead() {
        val syms = Outline.find("fun a() {}\r\nfun b() {}", lang("a.kt"))
        assertEquals(listOf("a", "b"), syms.map { it.name })
        assertEquals(listOf(1, 2), syms.map { it.line })
    }

    @Test
    fun emojiBeforeTheNameKeepsOffsetsInUtf16Units() {
        val src = "// 😀\nfun ok() {}"
        assertEquals(src.indexOf("ok"), Outline.find(src, lang("a.kt"))[0].offset)
    }

    @Test
    fun theListStopsAtTheLimit() {
        val src = "fun a() {}\n".repeat(50)
        assertEquals(7, Outline.find(src, lang("a.kt"), limit = 7).size)
    }

    @Test
    fun aSearchThatIsToldToStopEndsWithACancellation() {
        try {
            Outline.find("fun a() {}\n".repeat(1000), lang("a.kt"), stop = { true })
            fail()
        } catch (_: CancellationException) {
        }
    }

    @Test
    fun aBigTextIsReadQuickly() {
        val src = "class A {\n    fun m() {}\n}\n".repeat(80_000)
        val t0 = System.nanoTime()
        val syms = Outline.find(src, lang("a.kt"), limit = 1_000_000)
        assertEquals(160_000, syms.size)
        assertTrue("took ${(System.nanoTime() - t0) / 1_000_000} ms", (System.nanoTime() - t0) / 1_000_000 < 5000)
    }

    private val sample = """
        class Foo {
            fun a() {
                val x = 1

                val y = 2
            }

            fun b() {}
        }

        fun top() {}
    """.trimIndent()

    @Test
    fun theScopeIsTheEntriesThatContainTheCursor() {
        assertEquals(listOf("Foo", "a"), scope(sample, "a.kt", sample.indexOf("val x")))
        assertEquals(listOf("Foo", "a"), scope(sample, "a.kt", sample.indexOf("val y")))
        assertEquals(listOf("Foo", "b"), scope(sample, "a.kt", sample.indexOf("fun b") + 2))
        assertEquals(listOf("Foo"), scope(sample, "a.kt", sample.indexOf("class") + 1))
    }

    @Test
    fun aBlankLineInsideAFunctionIsInsideItAndOneBetweenFunctionsIsNot() {
        val blankInside = sample.indexOf("val x") + "val x = 1".length + 1 // the empty line
        assertEquals(listOf("Foo", "a"), scope(sample, "a.kt", blankInside))
        val between = sample.indexOf("}\n\nfun top") + 2
        assertEquals(emptyList<String>(), scope(sample, "a.kt", between))
        assertEquals(listOf("top"), scope(sample, "a.kt", sample.length - 3))
    }

    @Test
    fun theClosingBraceOfAFunctionIsNotInsideTheNextOne() {
        assertEquals(listOf("Foo"), scope(sample, "a.kt", sample.indexOf("    }\n\n    fun b") + 4))
    }

    @Test
    fun noScopeBeforeTheFirstEntryOrWithoutEntries() {
        assertEquals(emptyList<String>(), scope("x\n\nfun a() {}\n", "a.kt", 0))
        assertEquals(emptyList<String>(), scope("hello", "a.txt", 2))
        assertEquals(emptyList<String>(), Outline.scopeAt(emptyList(), "", 0).map { it.name })
        assertEquals(listOf("a"), scope("fun a() {}", "a.kt", 100)) // an offset past the end
    }

    @Test
    fun tabsCountAsFourSpaces() {
        val src = "class A {\n\tfun m() {\n\t\tx()\n\t}\n}\n"
        assertEquals(listOf("A", "m"), scope(src, "a.kt", src.indexOf("x()")))
    }

    @Test
    fun markdownScopeIsTheHeadingsAbove() {
        val md = "# A\ntext\n## B\nmore\n# C\nend\n"
        assertEquals(listOf("A", "B"), scope(md, "a.md", md.indexOf("more")))
        assertEquals(listOf("C"), scope(md, "a.md", md.indexOf("end")))
    }

    @Test
    fun languagesWithNoDefinitionWordsFindNothingInsteadOfFailing() {
        val bare = Lang("Bare")
        assertTrue(Outline.find("fun a() {}", bare).isEmpty())
        assertTrue(Languages::class.java.name.isNotEmpty())
    }

    private fun sym(name: String) = Outline.Symbol(1, 0, name, Outline.Kind.FUNCTION, 0)

    @Test
    fun scopeLabelJoinsNamesAndDropsTheOuterOnesWhenTooLong() {
        assertEquals("", Outline.label(emptyList()))
        assertEquals("run", Outline.label(listOf(sym("run"))))
        assertEquals("Foo \u203A run", Outline.label(listOf(sym("Foo"), sym("run"))))
        assertEquals("\u2026 \u203A run", Outline.label(listOf(sym("Foo"), sym("run")), max = 7))
        assertEquals("\u2026 \u203A bar \u203A run", Outline.label(listOf(sym("Foo"), sym("bar"), sym("run")), max = 13))
    }

    @Test
    fun scopeLabelCutsAnInnermostNameThatAloneIsTooLong() {
        val out = Outline.label(listOf(sym("a".repeat(80))), max = 10)
        assertEquals(10, out.length)
        assertTrue(out.endsWith("\u2026"))
    }
}
