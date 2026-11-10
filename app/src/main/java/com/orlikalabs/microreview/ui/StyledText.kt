package com.orlikalabs.microreview.ui

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.UnderlineSpan
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration

/** A card text styled by [com.orlikalabs.microreview.CardText.styled], for Compose: the same emphasis spans. */
fun CharSequence.toAnnotatedString(): AnnotatedString {
    if (this !is Spanned) return AnnotatedString(toString())
    val spanned = this
    return buildAnnotatedString {
        append(spanned.toString())
        spanned.getSpans(0, spanned.length, Any::class.java).forEach { span ->
            val style = when (span) {
                is StyleSpan -> SpanStyle(
                    fontWeight = if (span.style and Typeface.BOLD != 0) FontWeight.Bold else null,
                    fontStyle = if (span.style and Typeface.ITALIC != 0) FontStyle.Italic else null,
                )
                is UnderlineSpan -> SpanStyle(textDecoration = TextDecoration.Underline)
                is StrikethroughSpan -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                is SuperscriptSpan -> SpanStyle(baselineShift = BaselineShift.Superscript)
                is SubscriptSpan -> SpanStyle(baselineShift = BaselineShift.Subscript)
                else -> null
            } ?: return@forEach
            addStyle(style, spanned.getSpanStart(span), spanned.getSpanEnd(span))
        }
    }
}
