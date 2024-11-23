package ac.mdiq.podcini.preferences.fragments

import ac.mdiq.podcini.R
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.defaultPage
import ac.mdiq.podcini.preferences.UserPreferences.fullNotificationButtons
import ac.mdiq.podcini.preferences.UserPreferences.hiddenDrawerItems
import ac.mdiq.podcini.preferences.UserPreferences.setShowRemainTimeSetting
import ac.mdiq.podcini.ui.activity.PreferenceActivity
import ac.mdiq.podcini.ui.activity.PreferenceActivity.Companion.getTitleOfPage
import ac.mdiq.podcini.ui.activity.PreferenceActivity.Screens
import ac.mdiq.podcini.ui.activity.PreferenceActivity.SwipePreferencesFragment
import ac.mdiq.podcini.ui.fragment.NavDrawerFragment.Companion.navMap
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import android.content.Context
import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class UserInterfacePreferencesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_user_interface)
        setupInterfaceScreen()
    }

    override fun onStart() {
        super.onStart()
        (activity as PreferenceActivity).supportActionBar!!.setTitle(R.string.user_interface_label)
    }

    private fun setupInterfaceScreen() {
        val restartApp = Preference.OnPreferenceChangeListener { _: Preference?, _: Any? ->
            ActivityCompat.recreate(requireActivity())
            true
        }
        findPreference<Preference>(UserPreferences.Prefs.prefTheme.name)!!.onPreferenceChangeListener = restartApp
        findPreference<Preference>(UserPreferences.Prefs.prefThemeBlack.name)!!.onPreferenceChangeListener = restartApp
        findPreference<Preference>(UserPreferences.Prefs.prefTintedColors.name)!!.onPreferenceChangeListener = restartApp
        if (Build.VERSION.SDK_INT < 31) findPreference<Preference>(UserPreferences.Prefs.prefTintedColors.name)!!.isVisible = false

        findPreference<Preference>(UserPreferences.Prefs.showTimeLeft.name)?.setOnPreferenceChangeListener { _: Preference?, newValue: Any? ->
            setShowRemainTimeSetting(newValue as Boolean?)
            //            TODO: need another event type?
//            EventFlow.postEvent(FlowEvent.EpisodePlayedEvent())
            EventFlow.postEvent(FlowEvent.PlayerSettingsEvent())
            true
        }

        fun drawerPreferencesDialog(context: Context, callback: Runnable?) {
            val hiddenItems = hiddenDrawerItems.map { it.trim() }.toMutableSet()
//        val navTitles = context.resources.getStringArray(R.array.nav_drawer_titles)
            val navTitles = navMap.values.map { context.resources.getString(it.nameRes).trim() }.toTypedArray()
            val checked = BooleanArray(navMap.size)
            for (i in navMap.keys.indices) {
                val tag = navMap.keys.toList()[i]
                if (!hiddenItems.contains(tag)) checked[i] = true
            }
            val builder = MaterialAlertDialogBuilder(context)
            builder.setTitle(R.string.drawer_preferences)
            builder.setMultiChoiceItems(navTitles, checked) { _: DialogInterface?, which: Int, isChecked: Boolean ->
                if (isChecked) hiddenItems.remove(navMap.keys.toList()[which])
                else hiddenItems.add((navMap.keys.toList()[which]).trim())
            }
            builder.setPositiveButton(R.string.confirm_label) { _: DialogInterface?, _: Int ->
                hiddenDrawerItems = hiddenItems.toList()
                if (hiddenItems.contains(defaultPage)) {
                    for (tag in navMap.keys) {
                        if (!hiddenItems.contains(tag)) {
                            defaultPage = tag
                            break
                        }
                    }
                }
                callback?.run()
            }
            builder.setNegativeButton(R.string.cancel_label, null)
            builder.create().show()
        }

        findPreference<Preference>(UserPreferences.Prefs.prefHiddenDrawerItems.name)?.setOnPreferenceClickListener {
            drawerPreferencesDialog(requireContext(), null)
            true
        }

        findPreference<Preference>(UserPreferences.Prefs.prefFullNotificationButtons.name)?.setOnPreferenceClickListener {
            showFullNotificationButtonsDialog()
            true
        }
        findPreference<Preference>(PREF_SWIPE)?.setOnPreferenceClickListener {
            (activity as PreferenceActivity).openScreen(Screens.preferences_swipe)
            true
        }
        if (Build.VERSION.SDK_INT >= 26) findPreference<Preference>(UserPreferences.Prefs.prefExpandNotify.name)!!.isVisible = false
    }


    private fun showFullNotificationButtonsDialog() {
        val context: Context? = activity

        val preferredButtons = fullNotificationButtons
        val allButtonNames = context!!.resources.getStringArray(R.array.full_notification_buttons_options)
        val buttonIDs = intArrayOf(2, 3, 4)
        val exactItems = 2
        val completeListener = DialogInterface.OnClickListener { _: DialogInterface?, _: Int ->
            fullNotificationButtons = preferredButtons
        }
        val title = context.resources.getString(
            R.string.pref_full_notification_buttons_title)

        showNotificationButtonsDialog(preferredButtons.toMutableList(), allButtonNames, buttonIDs, title, exactItems, completeListener)
    }

    private fun showNotificationButtonsDialog(preferredButtons: MutableList<Int>?, allButtonNames: Array<String>, buttonIds: IntArray,
                                              title: String, exactItems: Int, completeListener: DialogInterface.OnClickListener) {
        val checked = BooleanArray(allButtonNames.size) // booleans default to false in java

        val context: Context? = activity

        // Clear buttons that are not part of the setting anymore
        for (i in preferredButtons!!.indices.reversed()) {
            var isValid = false
            for (j in checked.indices) {
                if (buttonIds[j] == preferredButtons[i]) {
                    isValid = true
                    break
                }
            }
            if (!isValid) preferredButtons.removeAt(i)
        }

        for (i in checked.indices) {
            if (preferredButtons.contains(buttonIds[i])) checked[i] = true
        }

        val builder = MaterialAlertDialogBuilder(context!!)
        builder.setTitle(title)
        builder.setMultiChoiceItems(allButtonNames,
            checked) { _: DialogInterface?, which: Int, isChecked: Boolean ->
            checked[which] = isChecked
            if (isChecked) preferredButtons.add(buttonIds[which])
            else preferredButtons.remove(buttonIds[which])
        }
        builder.setPositiveButton(R.string.confirm_label, null)
        builder.setNegativeButton(R.string.cancel_label, null)
        val dialog = builder.create()

        dialog.show()

        val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

        positiveButton.setOnClickListener {
            if (preferredButtons.size != exactItems) {
                val selectionView = dialog.listView
                Snackbar.make(selectionView, String.format(context.resources.getString(R.string.pref_compact_notification_buttons_dialog_error_exact), exactItems), Snackbar.LENGTH_SHORT).show()
            } else {
                completeListener.onClick(dialog, AlertDialog.BUTTON_POSITIVE)
                dialog.cancel()
            }
        }
    }

    companion object {
        private const val PREF_SWIPE = "prefSwipe"
    }
}
