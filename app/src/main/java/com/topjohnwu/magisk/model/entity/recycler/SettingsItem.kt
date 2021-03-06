package com.topjohnwu.magisk.model.entity.recycler

import android.content.Context
import android.content.res.Resources
import android.view.View
import androidx.annotation.ArrayRes
import androidx.annotation.CallSuper
import androidx.databinding.Bindable
import com.topjohnwu.magisk.BR
import com.topjohnwu.magisk.R
import com.topjohnwu.magisk.databinding.ObservableItem
import com.topjohnwu.magisk.utils.TransitiveText
import com.topjohnwu.magisk.utils.asTransitive
import com.topjohnwu.magisk.utils.set
import com.topjohnwu.magisk.view.MagiskDialog
import org.koin.core.KoinComponent
import org.koin.core.get

sealed class SettingsItem : ObservableItem<SettingsItem>() {

    override val layoutRes get() = R.layout.item_settings

    open val icon: Int get() = 0
    open val title: TransitiveText get() = TransitiveText.EMPTY
    @get:Bindable
    open val description: TransitiveText get() = TransitiveText.EMPTY

    // ---

    open val showSwitch get() = false

    @get:Bindable
    open val isChecked get() = false

    open fun onToggle(view: View, callback: Callback, checked: Boolean) {}

    // ---

    @get:Bindable
    var isEnabled = true
        set(value) = set(value, field, { field = it }, BR.enabled)

    open fun onPressed(view: View, callback: Callback) {
        callback.onItemPressed(view, this)
    }

    open fun refresh() {}

    override fun itemSameAs(other: SettingsItem) = this === other
    override fun contentSameAs(other: SettingsItem) = itemSameAs(other)

    // ---

    interface Callback {
        fun onItemPressed(view: View, item: SettingsItem, callback: () -> Unit = {})
        fun onItemChanged(view: View, item: SettingsItem)
    }

    // ---

    abstract class Value<T> : SettingsItem() {

        abstract var value: T

        protected var callbackVars: Pair<View, Callback>? = null

        @CallSuper
        override fun onPressed(view: View, callback: Callback) {
            callbackVars = view to callback
            callback.onItemPressed(view, this) {
                onPressed(view)
            }
        }

        abstract fun onPressed(view: View)

        protected inline fun <reified T> setV(
            new: T, old: T, setter: (T) -> Unit, afterChanged: (T) -> Unit = {}) {
            set(new, old, setter, BR.value, BR.description, BR.checked) {
                afterChanged(it)
                callbackVars?.let { (view, callback) ->
                    callbackVars = null
                    callback.onItemChanged(view, this)
                }
            }
        }
    }

    abstract class Toggle : Value<Boolean>() {

        override val showSwitch get() = true
        override val isChecked get() = value

        override fun onToggle(view: View, callback: Callback, checked: Boolean) =
            set(checked, value, { onPressed(view, callback) }, BR.checked)

        override fun onPressed(view: View) {
            value = !value
        }
    }

    abstract class Input : Value<String>() {

        protected abstract val inputResult: String?

        override fun onPressed(view: View) {
            MagiskDialog(view.context)
                .applyTitle(title.getText(view.resources))
                .applyView(getView(view.context))
                .applyButton(MagiskDialog.ButtonType.POSITIVE) {
                    titleRes = android.R.string.ok
                    onClick {
                        inputResult?.let { result ->
                            preventDismiss = false
                            value = result
                            it.dismiss()
                            return@onClick
                        }
                        preventDismiss = true
                    }
                }
                .applyButton(MagiskDialog.ButtonType.NEGATIVE) {
                    titleRes = android.R.string.cancel
                }
                .reveal()
        }

        abstract fun getView(context: Context): View
    }

    abstract class Selector : Value<Int>(), KoinComponent {

        protected val resources get() = get<Resources>()

        @ArrayRes open val entryRes = -1
        @ArrayRes open val entryValRes = -1

        open val entries get() = resources.getArrayOrEmpty(entryRes)
        open val entryValues get() = resources.getArrayOrEmpty(entryValRes)

        override val description: TransitiveText
            get() = entries.getOrNull(value)?.asTransitive() ?: TransitiveText.EMPTY

        private fun Resources.getArrayOrEmpty(id: Int): Array<String> =
            runCatching { getStringArray(id) }.getOrDefault(emptyArray())

        override fun onPressed(view: View, callback: Callback) {
            if (entries.isEmpty() || entryValues.isEmpty()) return
            super.onPressed(view, callback)
        }

        override fun onPressed(view: View) {
            MagiskDialog(view.context)
                .applyTitle(title.getText(resources))
                .applyButton(MagiskDialog.ButtonType.NEGATIVE) {
                    titleRes = android.R.string.cancel
                }
                .applyAdapter(entries) {
                    value = it
                }
                .reveal()
        }

    }

    abstract class Blank : SettingsItem()

    abstract class Section : SettingsItem() {
        override val layoutRes = R.layout.item_settings_section
    }

}
