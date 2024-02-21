package ac.mdiq.podcini.fragment.preferences

import android.content.Context
import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import ac.mdiq.podcini.R
import ac.mdiq.podcini.activity.PreferenceActivity
import ac.mdiq.podcini.dialog.DrawerPreferencesDialog
import ac.mdiq.podcini.dialog.FeedSortDialog
import ac.mdiq.podcini.dialog.SubscriptionsFilterDialog
import ac.mdiq.podcini.event.PlayerStatusEvent
import ac.mdiq.podcini.event.UnreadItemsUpdateEvent
import ac.mdiq.podcini.storage.preferences.UserPreferences
import ac.mdiq.podcini.storage.preferences.UserPreferences.fullNotificationButtons
import ac.mdiq.podcini.storage.preferences.UserPreferences.setShowRemainTimeSetting
import org.greenrobot.eventbus.EventBus

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
        val restartApp = Preference.OnPreferenceChangeListener { preference: Preference?, newValue: Any? ->
            ActivityCompat.recreate(requireActivity())
            true
        }
        findPreference<Preference>(UserPreferences.PREF_THEME)!!.onPreferenceChangeListener = restartApp
        findPreference<Preference>(UserPreferences.PREF_THEME_BLACK)!!.onPreferenceChangeListener = restartApp
        findPreference<Preference>(UserPreferences.PREF_TINTED_COLORS)!!.onPreferenceChangeListener = restartApp
        if (Build.VERSION.SDK_INT < 31) {
            findPreference<Preference>(UserPreferences.PREF_TINTED_COLORS)!!.isVisible = false
        }

        findPreference<Preference>(UserPreferences.PREF_SHOW_TIME_LEFT)
            ?.setOnPreferenceChangeListener { preference: Preference?, newValue: Any? ->
                setShowRemainTimeSetting(newValue as Boolean?)
                EventBus.getDefault().post(UnreadItemsUpdateEvent())
                EventBus.getDefault().post(PlayerStatusEvent())
                true
            }

        findPreference<Preference>(UserPreferences.PREF_HIDDEN_DRAWER_ITEMS)
            ?.setOnPreferenceClickListener { preference: Preference? ->
                DrawerPreferencesDialog.show(requireContext(), null)
                true
            }

        findPreference<Preference>(UserPreferences.PREF_FULL_NOTIFICATION_BUTTONS)
            ?.setOnPreferenceClickListener { preference: Preference? ->
                showFullNotificationButtonsDialog()
                true
            }
        findPreference<Preference>(UserPreferences.PREF_FILTER_FEED)?.onPreferenceClickListener =
            (Preference.OnPreferenceClickListener { preference: Preference? ->
                SubscriptionsFilterDialog().show(childFragmentManager, "filter")
                true
            })

        findPreference<Preference>(UserPreferences.PREF_DRAWER_FEED_ORDER)?.onPreferenceClickListener =
            (Preference.OnPreferenceClickListener { preference: Preference? ->
                FeedSortDialog.showDialog(requireContext())
                true
            })
        findPreference<Preference>(PREF_SWIPE)
            ?.setOnPreferenceClickListener { preference: Preference? ->
                (activity as PreferenceActivity).openScreen(R.xml.preferences_swipe)
                true
            }

        if (Build.VERSION.SDK_INT >= 26) {
            findPreference<Preference>(UserPreferences.PREF_EXPANDED_NOTIFICATION)!!.isVisible = false
        }
    }


    private fun showFullNotificationButtonsDialog() {
        val context: Context? = activity

        val preferredButtons = fullNotificationButtons
        val allButtonNames = context!!.resources.getStringArray(
            R.array.full_notification_buttons_options)
        val buttonIDs = intArrayOf(2, 3, 4)
        val exactItems = 2
        val completeListener = DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int ->
            fullNotificationButtons = preferredButtons
        }
        val title = context.resources.getString(
            R.string.pref_full_notification_buttons_title)

        showNotificationButtonsDialog(preferredButtons?.toMutableList(), allButtonNames, buttonIDs, title,
            exactItems, completeListener
        )
    }

    private fun showNotificationButtonsDialog(preferredButtons: MutableList<Int>?,
                                              allButtonNames: Array<String>, buttonIds: IntArray, title: String,
                                              exactItems: Int, completeListener: DialogInterface.OnClickListener
    ) {
        val checked = BooleanArray(allButtonNames.size) // booleans default to false in java

        val context: Context? = activity

        // Clear buttons that are not part of the setting anymore
        for (i in preferredButtons!!.indices.reversed()) {
            var isValid = false
            for (j in checked.indices) {
                if (buttonIds[j] == preferredButtons[i]) {
                    isValid = true
                }
            }

            if (!isValid) {
                preferredButtons.removeAt(i)
            }
        }

        for (i in checked.indices) {
            if (preferredButtons.contains(buttonIds[i])) {
                checked[i] = true
            }
        }

        val builder = MaterialAlertDialogBuilder(context!!)
        builder.setTitle(title)
        builder.setMultiChoiceItems(allButtonNames,
            checked) { dialog: DialogInterface?, which: Int, isChecked: Boolean ->
            checked[which] = isChecked
            if (isChecked) {
                preferredButtons.add(buttonIds[which])
            } else {
                preferredButtons.remove(buttonIds[which])
            }
        }
        builder.setPositiveButton(R.string.confirm_label, null)
        builder.setNegativeButton(R.string.cancel_label, null)
        val dialog = builder.create()

        dialog.show()

        val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

        positiveButton.setOnClickListener { v: View? ->
            if (preferredButtons.size != exactItems) {
                val selectionView = dialog.listView
                Snackbar.make(
                    selectionView,
                    String.format(context.resources.getString(
                        R.string.pref_compact_notification_buttons_dialog_error_exact), exactItems),
                    Snackbar.LENGTH_SHORT).show()
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