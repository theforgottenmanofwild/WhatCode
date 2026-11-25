package com.whatcodeeditor.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bracket matching works on plain text and numbers, so it can be checked without a device. */
class BracketMatchTest {

    /**
     * Finds the pair for [marked], where ¦ shows the cursor (and is not part of the text), with the strings and
     * comments that the real highlighter finds for a file called [file]. Returns the positions of the two brackets in
     * the text without the ¦, or null.
     */
    private fun find(marked: String, file: String = "a.kt"): Pair<Int, Int>? {
        val cursor = marked.indexOf('¦')
        val src = marked.replace("¦", "")
        val lang = Highlighter.languageFor(file)
        val skipped = BracketMatch.skipFrom(Highlighter.tokenize(src, lang))
        val pair = BracketMatch.find(src, cursor, lang, skipped) ?: return null
        return pair.open to pair.close
    }

    // ---------------------------------------------------------------- round, square and curly brackets

    @Test
    fun aBracketAfterTheCursorFindsItsPartner() {
        assertEquals(4 to 10, find("x = ¦(a + b)"))
        assertEquals(0 to 5, find("¦[1, 2]"))
        assertEquals(0 to 4, find("¦{a b}"))
    }

    @Test
    fun aBracketBeforeTheCursorFindsItsPartner() {
        assertEquals(0 to 6, find("(a + b)¦"))
        assertEquals(0 to 5, find("[1, 2]¦"))
        assertEquals(0 to 4, find("{a b}¦"))
    }

    @Test
    fun theBracketBeforeTheCursorIsTriedFirst() {
        assertEquals(0 to 2, find("(a)¦(b)"))
    }

    @Test
    fun ifTheBracketBeforeHasNoPartnerTheOneAfterIsUsed() {
        assertEquals(2 to 4, find("a)¦(b)"))
    }

    @Test
    fun nestedBracketsPairUpFromTheOutside() {
        // ((a)(b)) has its brackets at 0 1 3 4 6 7
        assertEquals(0 to 7, find("¦((a)(b))"))
        assertEquals(0 to 7, find("(¦(a)(b))"))
        assertEquals(1 to 3, find("((¦a)(b))"))
        assertEquals(1 to 3, find("((a)¦(b))"))
        assertEquals(4 to 6, find("((a)(b¦))"))
        assertEquals(0 to 7, find("((a)(b))¦"))
    }

    @Test
    fun otherKindsOfBracketsAreNotCountedWhileLooking() {
        assertEquals(0 to 3, find("¦(a[)b]"))
        assertEquals(0 to 7, find("¦{[a](b)}"))
    }

    @Test
    fun noPartnerOrNoBracketGivesNothing() {
        assertNull(find("¦(a"))
        assertNull(find("a)¦"))
        assertNull(find("¦"))
        assertNull(find("abc¦def"))
        assertNull(find("x¦"))
    }

    @Test
    fun aCursorOutsideTheTextGivesNothing() {
        val kotlin = Highlighter.languageFor("a.kt")
        assertNull(BracketMatch.find("(a)", -1, kotlin))
        assertNull(BracketMatch.find("(a)", 4, kotlin))
        assertEquals(0, BracketMatch.find("(a)", 0, kotlin)!!.open)
        assertEquals(2, BracketMatch.find("(a)", 3, kotlin)!!.close)
    }

    // ---------------------------------------------------------------- strings, comments, regular expressions

    @Test
    fun bracketsInsideAStringAreNotCounted() {
        val src = "f(\"(\", x)"
        assertEquals(1 to src.lastIndexOf(')'), find("f¦(\"(\", x)"))
        assertEquals(1 to src.lastIndexOf(')'), find("f(\"(\", x)¦"))
    }

    @Test
    fun aBracketInsideAStringHasNoPair() {
        assertNull(find("val s = \"a¦(b)\""))
        assertNull(find("val s = \"a(b¦)\""))
    }

    @Test
    fun bracketsInsideCommentsAreSkipped() {
        assertEquals(1 to 3, find("f¦(a) // )"))
        assertNull(find("// ¦(x)"))
        assertEquals(1 to 11, find("f¦(a /* ) */)"))
        assertNull(find("/* ¦( */"))
        assertEquals(1 to 3, find("p¦(a)\n# )", "a.py"))
    }

    @Test
    fun bracketsInsideARegularExpressionAreSkipped() {
        // f(/\)/) has its brackets at 1 and 6; the ) at 4 is part of the regular expression
        assertEquals(1 to 6, find("f¦(/\\)/)", "a.js"))
    }

    @Test
    fun withoutAnyKnowledgeOfStringsEveryBracketCounts() {
        val kotlin = Highlighter.languageFor("a.kt")
        // The ( inside the string is counted as a second opening bracket, so the first one never closes.
        assertNull(BracketMatch.find("f(\"(\", x)", 1, kotlin))
        assertEquals(1, BracketMatch.find("f(\"(\", x)", 1, kotlin) { it == 3 }!!.open)
    }

    // ---------------------------------------------------------------- angle brackets

    @Test
    fun genericsArePairedFromTheOutsideInAndTheInsideOut() {
        val src = "val m: Map<String, List<Int>> = x"
        val outer = src.indexOf('<')
        val inner = src.indexOf("<Int")
        val firstClose = src.indexOf(">>")
        val secondClose = firstClose + 1
        assertEquals(outer to secondClose, find("val m: Map¦<String, List<Int>> = x"))
        assertEquals(inner to firstClose, find("val m: Map<String, List¦<Int>> = x"))
        assertEquals(inner to firstClose, find("val m: Map<String, List<Int>¦> = x"))
        assertEquals(outer to secondClose, find("val m: Map<String, List<Int>>¦ = x"))
    }

    @Test
    fun genericsAreFoundInTheLanguagesThatHaveThem() {
        assertEquals(4 to 11, find("List¦<String> xs", "A.java"))
        assertEquals(5 to 12, find("Array¦<number>", "a.ts"))
        assertEquals(3 to 7, find("Vec¦<i32>", "a.rs"))
        assertEquals(5 to 9, find("Vec::¦<i32>::new()", "a.rs"))
        assertEquals(4 to 8, find("Task<int>¦ t;", "a.cs"))
        assertEquals(11 to 15, find("std::vector¦<int> v;", "a.cpp"))
    }

    @Test
    fun aCppTemplateWithASpaceBeforeTheBracketIsFound() {
        val src = "template <typename T>"
        assertEquals(src.indexOf('<') to src.indexOf('>'), find("template ¦<typename T>", "a.cpp"))
    }

    @Test
    fun comparisonsAndArrowsAreNotBrackets() {
        assertNull(find("if (a ¦< b && c > d) x"))
        assertNull(find("if (a < b && c ¦> d) x"))
        assertNull(find("a ¦<= b"))
        assertNull(find("a ¦>= b"))
        assertNull(find("x -¦> y"))
        assertNull(find("val f = { x ->¦ x }"))
        assertNull(find("a ¦<< 2"))
    }

    @Test
    fun aLessThanThatTouchesAWordButNeverClosesIsNotAPair() {
        // It looks like the start of generics, but a ; or a { or a && shows that it is only a comparison.
        assertNull(find("if (a¦<b) { c > d }"))
        assertNull(find("x = a¦<b; y = c>d"))
        assertNull(find("a¦<b && c>d"))
        assertNull(find("b<c || d¦>e")) // going back from the > meets ||
    }

    @Test
    fun angleBracketsAreLeftAloneWhereTheyMeanNothing() {
        assertNull(find("a¦<b>c", "a.py"))
        assertNull(find("Array¦<T>", "a.js"))
        assertNull(find("#include ¦<stdio.h>", "a.c"))
        assertNull(find("a¦<b>c", "a.txt"))
        assertNull(find("a¦<b>c", "a.md"))
        assertNull(find("<¦a>", "a.json"))
    }

    @Test
    fun theTwoEndsOfATagArePaired() {
        assertEquals(0 to 4, find("¦<div>", "a.html"))
        assertEquals(0 to 4, find("<div¦>", "a.xml"))
        assertEquals(6 to 11, find("<div>x¦</div>", "a.html"))
        val svg = "<svg width=\"1\">"
        assertEquals(0 to svg.indexOf('>'), find("¦$svg", "a.svg"))
    }

    @Test
    fun aBracketInsideAnAttributeValueDoesNotEndTheTag() {
        val src = "<div class=\"a>b\">x</div>"
        assertEquals(0 to src.indexOf("\">") + 1, find("¦$src", "a.html"))
    }

    @Test
    fun aMarkupCommentIsNotPaired() {
        assertNull(find("<!-- ¦<b> -->", "a.html"))
        assertEquals(0 to 2, find("¦<b> <!-- > -->", "a.html"))
    }

    // ---------------------------------------------------------------- limits

    @Test
    fun theSearchStopsAfterTheScanLimit() {
        val kotlin = Highlighter.languageFor("a.kt")
        val near = "(" + "x".repeat(BracketMatch.MAX_SCAN - 1) + ")"
        assertEquals(BracketMatch.MAX_SCAN, BracketMatch.find(near, 0, kotlin)!!.close)
        val far = "(" + "x".repeat(BracketMatch.MAX_SCAN) + ")"
        assertNull(BracketMatch.find(far, 0, kotlin))
        val back = BracketMatch.find(near, near.length, kotlin)!!
        assertEquals(BracketMatch.MAX_SCAN, back.close - back.open)
        assertNull(BracketMatch.find(far, far.length, kotlin))
    }

    @Test
    fun aHugeLineOnlyLooksAtTheScanLimit() {
        val kotlin = Highlighter.languageFor("a.kt")
        var asked = 0
        val opens = "(".repeat(5_000_000)
        assertNull(BracketMatch.find(opens, 0, kotlin) { asked++; false })
        assertTrue("looked at $asked characters going forward", asked <= BracketMatch.MAX_SCAN + 2)
        asked = 0
        val closes = ")".repeat(5_000_000)
        assertNull(BracketMatch.find(closes, closes.length, kotlin) { asked++; false })
        assertTrue("looked at $asked characters going back", asked <= BracketMatch.MAX_SCAN + 2)
    }

    @Test
    fun aBigFileWithManyBracketsIsQuick() {
        val kotlin = Highlighter.languageFor("a.kt")
        val text = "fun f(a: Int) { g(a) }\n".repeat(200_000)
        val start = System.nanoTime()
        repeat(200) { BracketMatch.find(text, 100_000 + it, kotlin) }
        val millis = (System.nanoTime() - start) / 1_000_000
        assertTrue("200 lookups took $millis ms", millis < 3_000)
    }

    // ---------------------------------------------------------------- what counts as a string or a comment

    @Test
    fun skipFromKnowsWhereStringsAndCommentsAre() {
        val src = "a = \"xy\" // c"
        val skipped = BracketMatch.skipFrom(Highlighter.tokenize(src, Highlighter.languageFor("a.kt")))
        assertTrue(!skipped(0))
        assertTrue(!skipped(src.indexOf('"') - 1))
        assertTrue(skipped(src.indexOf('"')))
        assertTrue(skipped(src.indexOf("y")))
        assertTrue(skipped(src.lastIndexOf('"')))
        assertTrue(!skipped(src.lastIndexOf('"') + 1))
        assertTrue(skipped(src.indexOf("//")))
        assertTrue(skipped(src.length - 1))
        assertTrue(!skipped(src.length))
    }

    @Test
    fun skipFromIsEmptyForPlainText() {
        val skipped = BracketMatch.skipFrom(Highlighter.tokenize("(\"a\") // b", Highlighter.languageFor("a.txt")))
        for (i in 0..10) assertTrue(!skipped(i))
    }
}
