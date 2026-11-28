package com.whatcode.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewLogicTest {

    @Test
    fun convertsMarkdownElementsSafelyAndDetectsPreviewableFiles() {
        assertTrue(PreviewLogic.isMarkdown("README.md"))
        assertTrue(PreviewLogic.isHtml("index.html"))
        assertFalse(PreviewLogic.isPreviewable("Main.kt"))

        val md = """
            # Title

            Hello **world** with `code` and <script>alert(1)</script>

            - item 1
            - item 2

            ```kt
            val x = 1
            ```
        """.trimIndent()
        val html = PreviewLogic.markdownToHtml(md, title = "Notes")
        assertTrue(html.contains("<h1>Title</h1>"))
        assertTrue(html.contains("<strong>world</strong>"))
        assertTrue(html.contains("<code>code</code>"))
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"))
        assertTrue(html.contains("<ul>"))
        assertTrue(html.contains("<pre><code>val x = 1"))
    }
}
