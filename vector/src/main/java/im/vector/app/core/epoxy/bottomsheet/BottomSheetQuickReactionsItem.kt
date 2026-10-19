/*
 * Copyright 2019-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */
package im.vector.app.core.epoxy.bottomsheet

import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import android.widget.TextView
import androidx.core.view.isVisible
import com.airbnb.epoxy.EpoxyAttribute
import com.airbnb.epoxy.EpoxyModelClass
import im.vector.app.EmojiCompatFontProvider
import im.vector.app.R
import im.vector.app.core.epoxy.VectorEpoxyHolder
import im.vector.app.core.epoxy.VectorEpoxyModel
import im.vector.app.core.epoxy.onClick
import im.vector.app.core.utils.isAnimationEnabled

/**
 * A quick reaction list for bottom sheet.
 */
@EpoxyModelClass
abstract class BottomSheetQuickReactionsItem : VectorEpoxyModel<BottomSheetQuickReactionsItem.Holder>(R.layout.item_bottom_sheet_quick_reaction) {

    @EpoxyAttribute
    lateinit var fontProvider: EmojiCompatFontProvider

    @EpoxyAttribute
    lateinit var texts: List<String>

    @EpoxyAttribute
    lateinit var selecteds: List<Boolean>

    @EpoxyAttribute
    var listener: Listener? = null

    override fun bind(holder: Holder) {
        super.bind(holder)
        holder.textViews.forEachIndexed { index, textView ->
            val reaction = texts.getOrNull(index)
            val isSelected = selecteds.getOrNull(index) == true

            textView.isVisible = reaction != null
            textView.isEnabled = reaction != null
            textView.typeface = fontProvider.typeface ?: Typeface.DEFAULT
            textView.text = reaction.orEmpty()
            textView.contentDescription = reaction.orEmpty()
            textView.isSelected = isSelected
            textView.isActivated = isSelected
            // A selected quick reaction should be highlighted, not faded almost invisible.
            // The background selector supplies a soft accent fill and a normal pressed ripple.
            textView.alpha = 1f

            // RecyclerView/Epoxy may reuse a view while it is still scaled from a previous bind.
            textView.animate().cancel()
            textView.scaleX = 1f
            textView.scaleY = 1f

            if (reaction == null) {
                // Server/user preference sets may contain fewer than the eight reusable views.
                // Hide unused slots and make sure an old listener cannot react to an empty chip.
                textView.onClick(null)
                return@forEachIndexed
            }

            textView.onClick {
                // Haptics and reduced-motion settings are independent: preserve the
                // touch acknowledgement even when the user disables animations.
                textView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                if (textView.context.isAnimationEnabled()) {
                    textView.animate().cancel()
                    textView.scaleX = 0.88f
                    textView.scaleY = 0.88f
                    textView.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(140L)
                            .start()
                } else {
                    textView.scaleX = 1f
                    textView.scaleY = 1f
                }
                listener?.didSelect(reaction, !isSelected)
            }
        }
    }

    class Holder : VectorEpoxyHolder() {
        private val quickReaction0 by bind<TextView>(R.id.quickReaction0)
        private val quickReaction1 by bind<TextView>(R.id.quickReaction1)
        private val quickReaction2 by bind<TextView>(R.id.quickReaction2)
        private val quickReaction3 by bind<TextView>(R.id.quickReaction3)
        private val quickReaction4 by bind<TextView>(R.id.quickReaction4)
        private val quickReaction5 by bind<TextView>(R.id.quickReaction5)
        private val quickReaction6 by bind<TextView>(R.id.quickReaction6)
        private val quickReaction7 by bind<TextView>(R.id.quickReaction7)

        val textViews
            get() = listOf(
                    quickReaction0,
                    quickReaction1,
                    quickReaction2,
                    quickReaction3,
                    quickReaction4,
                    quickReaction5,
                    quickReaction6,
                    quickReaction7
            )
    }

    interface Listener {
        fun didSelect(emoji: String, selected: Boolean)
    }
}
