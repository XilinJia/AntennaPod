package ac.mdiq.podcini.preferences.fragments

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.utils.NetworkUtils.autodownloadSelectedNetworks
import ac.mdiq.podcini.net.utils.NetworkUtils.isEnableAutodownloadWifiFilter
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.appPrefs
import ac.mdiq.podcini.preferences.UserPreferences.isEnableAutodownload
import ac.mdiq.podcini.ui.activity.PreferenceActivity
import ac.mdiq.podcini.util.Logd
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class AutoDownloadPreferencesFragment : PreferenceFragmentCompat() {
    private var selectedNetworks: Array<CheckBoxPreference?>? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_autodownload)

        setupAutoDownloadScreen()
        buildAutodownloadSelectedNetworksPreference()
        setSelectedNetworksEnabled(isEnableAutodownloadWifiFilter)
        buildEpisodeCleanupPreference()
    }

    override fun onStart() {
        super.onStart()
        (activity as PreferenceActivity).supportActionBar!!.setTitle(R.string.pref_automatic_download_title)
    }

    override fun onResume() {
        super.onResume()
        checkAutodownloadItemVisibility(isEnableAutodownload)
    }

    private fun setupAutoDownloadScreen() {
        findPreference<Preference>(UserPreferences.Prefs.prefEnableAutoDl.name)!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any? ->
                if (newValue is Boolean) checkAutodownloadItemVisibility(newValue)
                true
            }
        if (Build.VERSION.SDK_INT >= 29) findPreference<Preference>(UserPreferences.Prefs.prefEnableAutoDownloadWifiFilter.name)!!.isVisible = false
        findPreference<Preference>(UserPreferences.Prefs.prefEnableAutoDownloadWifiFilter.name)?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any? ->
                if (newValue is Boolean) {
                    setSelectedNetworksEnabled(newValue)
                    return@OnPreferenceChangeListener true
                } else return@OnPreferenceChangeListener false
            }
    }

    private fun checkAutodownloadItemVisibility(autoDownload: Boolean) {
        findPreference<Preference>(UserPreferences.Prefs.prefEpisodeCacheSize.name)!!.isEnabled = autoDownload
        findPreference<Preference>(UserPreferences.Prefs.prefEnableAutoDownloadOnBattery.name)!!.isEnabled = autoDownload
        findPreference<Preference>(UserPreferences.Prefs.prefEnableAutoDownloadWifiFilter.name)!!.isEnabled = autoDownload
        findPreference<Preference>(UserPreferences.Prefs.prefEpisodeCleanup.name)!!.isEnabled = autoDownload
        setSelectedNetworksEnabled(autoDownload && isEnableAutodownloadWifiFilter)
    }

    @SuppressLint("MissingPermission") // getConfiguredNetworks needs location permission starting with API 29
    private fun buildAutodownloadSelectedNetworksPreference() {
        if (Build.VERSION.SDK_INT >= 29) return
        val activity: Activity? = activity

        if (selectedNetworks != null) clearAutodownloadSelectedNetworsPreference()

        // get configured networks
        val wifiservice = activity!!.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val networks = wifiservice.configuredNetworks

        if (networks == null) {
            Log.e(TAG, "Couldn't get list of configure Wi-Fi networks")
            return
        }
        networks.sortWith { x: WifiConfiguration, y: WifiConfiguration -> blankIfNull(x.SSID).compareTo(blankIfNull(y.SSID), ignoreCase = true) }
        selectedNetworks = arrayOfNulls(networks.size)
        val prefValues = listOf(*autodownloadSelectedNetworks)
        val prefScreen = preferenceScreen
        val clickListener = Preference.OnPreferenceClickListener { preference: Preference ->
            if (preference is CheckBoxPreference) {
                val key = preference.getKey()
                val prefValuesList: MutableList<String?> = ArrayList(listOf(*autodownloadSelectedNetworks))
                val newValue = preference.isChecked
                Logd(TAG, "Selected network $key. New state: $newValue")

                val index = prefValuesList.indexOf(key)
                when {
                    // remove network
                    index >= 0 && !newValue -> prefValuesList.removeAt(index)
                    index < 0 && newValue -> prefValuesList.add(key)
                }

                setAutodownloadSelectedNetworks(prefValuesList.toTypedArray<String?>())
                return@OnPreferenceClickListener true
            } else return@OnPreferenceClickListener false
        }
        // create preference for each known network. attach listener and set
        // value
        for (i in networks.indices) {
            val config = networks[i]

            val pref = CheckBoxPreference(activity)
            val key = config.networkId.toString()
            pref.title = config.SSID
            pref.key = key
            pref.onPreferenceClickListener = clickListener
            pref.isPersistent = false
            pref.isChecked = prefValues.contains(key)
            selectedNetworks!![i] = pref
            prefScreen.addPreference(pref)
        }
    }

    private fun setAutodownloadSelectedNetworks(value: Array<String?>?) {
        appPrefs.edit().putString(UserPreferences.Prefs.prefAutodownloadSelectedNetworks.name, value!!.joinToString()).apply()
    }

    private fun clearAutodownloadSelectedNetworsPreference() {
        if (selectedNetworks != null) {
            val prefScreen = preferenceScreen
            for (network in selectedNetworks!!) if (network != null) prefScreen.removePreference(network)
        }
    }

    private fun buildEpisodeCleanupPreference() {
        val res = requireActivity().resources

        val pref = findPreference<ListPreference>(UserPreferences.Prefs.prefEpisodeCleanup.name)
        val values = res.getStringArray(R.array.episode_cleanup_values)
        val entries = arrayOfNulls<String>(values.size)
        for (x in values.indices) {
            when (val v = values[x].toInt()) {
                UserPreferences.EPISODE_CLEANUP_EXCEPT_FAVORITE -> entries[x] = res.getString(R.string.episode_cleanup_except_favorite_removal)
                UserPreferences.EPISODE_CLEANUP_QUEUE -> entries[x] = res.getString(R.string.episode_cleanup_queue_removal)
                UserPreferences.EPISODE_CLEANUP_NULL -> entries[x] = res.getString(R.string.episode_cleanup_never)
                0 -> entries[x] = res.getString(R.string.episode_cleanup_after_listening)
                in 1..23 -> entries[x] = res.getQuantityString(R.plurals.episode_cleanup_hours_after_listening, v, v)
                else -> {
                    val numDays = v / 24 // assume underlying value will be NOT fraction of days, e.g., 36 (hours)
                    entries[x] = res.getQuantityString(R.plurals.episode_cleanup_days_after_listening, numDays, numDays)
                }
            }
        }
        pref!!.entries = entries
    }

    private fun setSelectedNetworksEnabled(b: Boolean) {
        if (selectedNetworks != null) for (p in selectedNetworks!!) p!!.isEnabled = b
    }

    companion object {
        private val TAG: String = AutoDownloadPreferencesFragment::class.simpleName ?: "Anonymous"

        private fun blankIfNull(`val`: String?): String {
            return `val` ?: ""
        }
    }
}
