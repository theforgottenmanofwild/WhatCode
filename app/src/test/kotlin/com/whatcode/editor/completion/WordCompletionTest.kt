package com.whatcode.editor.completion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WordCompletionTest {

    private fun suggest(text: String, prefix: String, others: List<String> = emptyList(), limit: Int = 8): List<String> =
        WordCompletion.suggest(text, text.length, prefix, others, limit)

    @Test
    fun theWordBeforeTheCursorIsFound() {
        assertEquals(0, WordCompletion.prefixStart("count", 5))
        assertEquals(4, WordCompletion.prefixStart("let count", 9))
        assertEquals(1, WordCompletion.prefixStart("(ab", 3))
    }

    @Test
    fun noWordIsFoundWhenTheCursorIsNotRightAfterOne() {
        assertEquals(-1, WordCompletion.prefixStart("count ", 6))
        assertEquals(-1, WordCompletion.prefixStart("", 0))
        assertEquals(-1, WordCompletion.prefixStart("count", 2)) // the word goes on after the cursor
        assertEquals(-1, WordCompletion.prefixStart("count", 9)) // past the end
    }

    @Test
    fun aNumberIsNotCompleted() {
        assertEquals(-1, WordCompletion.prefixStart("123", 3))
        assertEquals(-1, WordCompletion.prefixStart("x = 0x1f", 8))
        assertEquals(0, WordCompletion.prefixStart("a1", 2))
    }

    @Test
    fun aWordLongerThanAWordCanBeIsLeftAlone() {
        val long = "a".repeat(WordCompletion.MAX_WORD + 10)
        assertEquals(-1, WordCompletion.prefixStart(long, long.length))
    }

    @Test
    fun underscoresAndAccentedLettersAreWordLetters() {
        assertEquals(0, WordCompletion.prefixStart("_my_var", 7))
        assertEquals(0, WordCompletion.prefixStart("caf\u00e9", 4))
        assertEquals(listOf("caf\u00e9s"), suggest("caf\u00e9s caf", "caf"))
    }

    @Test
    fun wordsStartingWithThePrefixAreSuggested() {
        assertEquals(listOf("count", "countdown", "counter"), suggest("counter count countdown other cou", "cou").sorted())
    }

    @Test
    fun theWordBeingTypedIsNotSuggestedAndNeitherIsTheSameWord() {
        assertEquals(emptyList<String>(), suggest("count", "count"))
        assertEquals(emptyList<String>(), suggest("cou", "cou"))
        assertEquals(emptyList<String>(), suggest("count count", "count"))
    }

    @Test
    fun theClosestWordComesFirst() {
        val text = "far_away " + "x ".repeat(50) + "farther " + "y ".repeat(5) + "fa"
        assertEquals(listOf("farther", "far_away"), suggest(text, "fa"))
    }

    @Test
    fun aWordWithTheCapitalsOfThePrefixComesBeforeOneThatNeedsThemIgnored() {
        assertEquals(listOf("FooBaz", "fooBar"), suggest("fooBar FooBaz Fo", "Fo"))
    }

    @Test
    fun capitalsAreIgnoredWhenLookingForWords() {
        assertEquals(listOf("HttpClient"), suggest("HttpClient htt", "htt"))
    }

    @Test
    fun wordsOfOtherFilesComeAfterWordsOfThisOne() {
        assertEquals(listOf("alpha", "alpine", "alto"), suggest("alpha al", "al", others = listOf("alpine alpine alto")))
    }

    @Test
    fun aWordOfThisFileIsNotRepeatedFromAnotherFile() {
        assertEquals(listOf("alpha"), suggest("alpha al", "al", others = listOf("alpha alpha")))
    }

    @Test
    fun wordsOfOtherFilesAreOrderedByHowOftenTheyAppear() {
        assertEquals(listOf("tree", "trace"), suggest("zz tr", "tr", others = listOf("trace tree tree tree", "trace")).take(2))
    }

    @Test
    fun noMoreThanTheLimitAreReturned() {
        val text = (1..30).joinToString(" ") { "item$it" } + " it"
        assertEquals(5, suggest(text, "it", limit = 5).size)
        assertEquals(emptyList<String>(), suggest(text, "it", limit = 0))
    }

    @Test
    fun anEmptyPrefixSuggestsNothing() {
        assertEquals(emptyList<String>(), suggest("anything at all", ""))
    }

    @Test
    fun aWordIsNotFoundHalfwayInsideAnother() {
        assertEquals(emptyList<String>(), suggest("subcount cou", "cou"))
    }

    @Test
    fun onlyAStretchAroundTheCursorIsReadInTheFileBeingEdited() {
        val text = "unreachable " + " ".repeat(WordCompletion.AROUND + 10) + "unr"
        assertEquals(emptyList<String>(), suggest(text, "unr"))
    }

    @Test
    fun otherFilesAreReadOnlyUpToTheirFirstCharacters() {
        val other = " ".repeat(WordCompletion.OTHER_CHARS + 10) + "hidden"
        assertEquals(emptyList<String>(), suggest("hi", "hi", others = listOf(other)))
        assertEquals(listOf("hidden"), suggest("hi", "hi", others = listOf("hidden")))
    }

    @Test
    fun onlyTheFirstOtherFilesAreRead() {
        val others = List(WordCompletion.MAX_OTHER_FILES + 1) { i -> if (i == WordCompletion.MAX_OTHER_FILES) "lastword" else "filler$i" }
        assertEquals(emptyList<String>(), suggest("la", "la", others = others))
    }

    @Test
    fun emojiAndLineEndingsDoNotBreakTheSearch() {
        val found = suggest("caf\r\nname\uD83D\uDE00 nameplate\r\nna", "na")
        assertTrue(found.contains("nameplate"))
        assertFalse(found.any { it.contains('\uD83D') })
    }

    @Test
    fun aStoppedSearchThrows() {
        var asked = 0
        val text = "word ".repeat(10_000) + "wo"
        try {
            WordCompletion.suggest(text, text.length, "wo", emptyList(), stop = { asked++; true })
            fail("expected the search to stop")
        } catch (_: WordCompletion.Stopped) {
            assertTrue(asked >= 1)
        }
    }

    @Test
    fun aSearchThatIsNotStoppedFinishes() {
        assertEquals(listOf("word"), suggest("word ".repeat(10_000) + "wo", "wo"))
    }
}
