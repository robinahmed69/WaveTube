package org.schabi.newpipe.views

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatTextView

/**
 * Shows "Modified by Robin Ahmed" where only the name is clickable
 * and opens the Facebook page.
 */
class CreditTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {

    init {
        val prefix = "Modified by "
        val spannable = SpannableString(prefix + CREDIT_NAME)
        val linkSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(CREDIT_URL))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    widget.context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    // no browser installed, nothing to do
                }
            }

            override fun updateDrawState(ds: TextPaint) {
                ds.color = currentTextColor
                ds.isUnderlineText = true
            }
        }
        spannable.setSpan(
            linkSpan,
            prefix.length,
            spannable.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        text = spannable
        movementMethod = LinkMovementMethod.getInstance()
        highlightColor = Color.TRANSPARENT
    }

    companion object {
        private const val CREDIT_NAME = "Robin Ahmed"
        private const val CREDIT_URL = "https://www.facebook.com/vetkimasss"
    }
}
