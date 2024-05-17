package ac.mdiq.podcini.preferences.fragments.about

import android.R.color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.ListFragment
import ac.mdiq.podcini.ui.adapter.SimpleIconListAdapter
import androidx.lifecycle.lifecycleScope
import io.reactivex.Single
import io.reactivex.SingleEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class TranslatorsFragment : ListFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.divider = null
        listView.setSelector(color.transparent)

        lifecycleScope.launch(Dispatchers.IO) {
            val translators = ArrayList<SimpleIconListAdapter.ListItem>()
            val reader = BufferedReader(InputStreamReader(requireContext().assets.open("translators.csv"), "UTF-8"))
            var line = ""
            while ((reader.readLine()?.also { line = it }) != null) {
                val info = line.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                translators.add(SimpleIconListAdapter.ListItem(info[0], info[1], ""))
            }
            withContext(Dispatchers.Main) {
                listAdapter = SimpleIconListAdapter(requireContext(), translators)         }
        }.invokeOnCompletion { throwable ->
            if (throwable != null) Toast.makeText(context, throwable.message, Toast.LENGTH_LONG).show()
        }
    }
}
