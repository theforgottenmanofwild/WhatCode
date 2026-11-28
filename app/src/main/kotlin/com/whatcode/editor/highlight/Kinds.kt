package com.whatcode.editor.highlight

import com.whatcode.editor.SyntaxRole

// The scanner marks every piece with the dark-theme colour of its role. A SyntaxScheme turns the marker into
// the colour that is shown (light theme, or one the person chose), when the pieces are painted.
internal val KEYWORD = SyntaxRole.KEYWORD.darkDefault
internal val CONTROL = SyntaxRole.CONTROL.darkDefault
internal val TYPE = SyntaxRole.TYPE.darkDefault
internal val FUNCTION = SyntaxRole.FUNCTION.darkDefault
internal val CONSTANT = SyntaxRole.CONSTANT.darkDefault
internal val PROPERTY = SyntaxRole.PROPERTY.darkDefault
internal val STRING = SyntaxRole.STRING.darkDefault
internal val NUMBER = SyntaxRole.NUMBER.darkDefault
internal val COMMENT = SyntaxRole.COMMENT.darkDefault
internal val REGEX = SyntaxRole.REGEX.darkDefault
internal val SELECTOR = SyntaxRole.SELECTOR.darkDefault

// Kinds of piece that need a decision in code. Real colours are large negative numbers, so these never clash.
internal const val S_WORD = 1
internal const val S_STRING = 2
internal const val S_REGEX = 3
internal const val S_KEYLEAD = 4
internal const val S_TYPELEAD = 5
internal const val S_QSTR = 6
internal const val S_TAGOPEN = 7
internal const val S_TAGEND = 8
internal const val S_TSTR = 9
internal const val S_ATTR = 10
internal const val S_ENTITY = 11
internal const val S_AT = 12
internal const val S_PROP = 13
internal const val S_HASH = 14
internal const val S_CLASS = 15
internal const val S_PSEUDO = 16
internal const val S_PUNCT = 17
internal const val S_IDENT = 18
internal const val S_URL = 19

internal fun words(s: String): Set<String> = s.split(' ').filter { it.isNotEmpty() }.toSet()

/** True if [marker], the colour a scanner gave a piece, is that of a string, a comment or a regular expression. */
internal fun isLiteralMarker(marker: Int): Boolean = marker == STRING || marker == COMMENT || marker == REGEX
