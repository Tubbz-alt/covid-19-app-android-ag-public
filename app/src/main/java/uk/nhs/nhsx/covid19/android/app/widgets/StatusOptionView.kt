package uk.nhs.nhsx.covid19.android.app.widgets

import android.R.attr
import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import android.widget.LinearLayout
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import kotlinx.android.synthetic.main.view_status_option.view.statusOptionIcon
import kotlinx.android.synthetic.main.view_status_option.view.statusOptionIconContainer
import kotlinx.android.synthetic.main.view_status_option.view.statusOptionLinkIndicator
import kotlinx.android.synthetic.main.view_status_option.view.statusOptionText
import uk.nhs.nhsx.covid19.android.app.R
import uk.nhs.nhsx.covid19.android.app.util.viewutils.dpToPx

open class StatusOptionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    var text: String? = ""
        set(value) {
            field = value
            statusOptionText.text = value
        }

    private var attrIsExternalLink: Boolean = false

    init {
        initializeViews()
        applyAttributes(context, attrs)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        val infoCompat = AccessibilityNodeInfoCompat.wrap(info)

        if (attrIsExternalLink) {
            info.contentDescription =
                context.getString(R.string.accessibility_announcement_link, statusOptionText.text)
            info.addAction(
                AccessibilityAction(
                    AccessibilityNodeInfoCompat.ACTION_CLICK,
                    context.getString(R.string.open_in_browser_warning)
                )
            )
        } else {
            info.contentDescription =
                context.getString(R.string.accessibility_announcement_button, statusOptionText.text)
        }
    }

    private fun initializeViews() {
        View.inflate(context, R.layout.view_status_option, this)
        configureLayout()
    }

    private fun applyAttributes(context: Context, attrs: AttributeSet?) {
        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.StatusOptionView,
            0,
            0
        ).apply {
            val attrOptionIcon = getDrawable(R.styleable.StatusOptionView_optionIcon)
            val attrOptionIconBackgroundColor =
                getColor(R.styleable.StatusOptionView_optionIconBackgroundColor, -1)
            text = getString(R.styleable.StatusOptionView_optionText)
            attrIsExternalLink = getBoolean(R.styleable.StatusOptionView_optionExternalLink, false)

            statusOptionText.text = text
            statusOptionText.setPaddingRelative(16.dpToPx.toInt(), 0, 0, 0)

            statusOptionIconContainer.backgroundTintList =
                ColorStateList.valueOf(attrOptionIconBackgroundColor)

            attrOptionIcon?.isAutoMirrored = true
            statusOptionIcon.setImageDrawable(attrOptionIcon)

            val linkIndicator =
                context.getDrawable(if (attrIsExternalLink) R.drawable.ic_link_status_option_view else R.drawable.ic_chevron_right)
            linkIndicator?.isAutoMirrored = true
            statusOptionLinkIndicator.setImageDrawable(linkIndicator)

            recycle()
        }
    }

    private fun configureLayout() {
        minimumHeight = 56.dpToPx.toInt()
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundResource(R.drawable.status_option_background)
        setSelectableItemForeground()
    }

    private fun setSelectableItemForeground() {
        val outValue = TypedValue()
        context.theme.resolveAttribute(attr.selectableItemBackground, outValue, true)
        foreground = context.getDrawable(outValue.resourceId)
    }
}
