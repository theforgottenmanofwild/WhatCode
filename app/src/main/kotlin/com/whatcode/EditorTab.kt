package com.whatcode

import android.net.Uri
import com.whatcode.editor.CodeEditText
import com.whatcode.editor.Navigation
import com.whatcode.files.TextEncoding

/**
 * One open file. The tab that is showing keeps its text in the editor and its state in the fields of
 * [MainActivity]; every other tab keeps its text, cursor and undo history here.
 */
internal class Tab(var uri: Uri?, var name: String) {
    var text: String = ""
    var modified = false
    var readOnly = false
    var encoding: TextEncoding.Spec = TextEncoding.Spec.UTF_8
    var diskModified: Long = -1L
    var diskSize: Long = -1L
    var selStart = 0
    var selEnd = 0
    var scrollY = 0
    var history: CodeEditText.History? = null
    var navigation: Navigation? = null
}
