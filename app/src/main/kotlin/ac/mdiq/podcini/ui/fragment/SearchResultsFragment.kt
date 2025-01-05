package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.feed.searcher.PodcastSearchResult
import ac.mdiq.podcini.net.feed.searcher.PodcastSearcher
import ac.mdiq.podcini.net.feed.searcher.PodcastSearcherRegistry
import ac.mdiq.podcini.net.utils.NetworkUtils.prepareUrl
import ac.mdiq.podcini.storage.database.Feeds.getFeedList
import ac.mdiq.podcini.storage.model.SubscriptionLog.Companion.getFeedLogMap
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.compose.OnlineFeedItem
import ac.mdiq.podcini.ui.compose.SearchBarRow
import ac.mdiq.podcini.util.Logd
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchResultsFragment : Fragment() {

    class SearchResultsVM {
        internal var searchProvider: PodcastSearcher? = null

        internal val feedLogs = getFeedLogMap()

        internal var defaultText by mutableStateOf("")
        internal var searchResults = mutableStateListOf<PodcastSearchResult>()
        internal var errorText by mutableStateOf("")
        internal var retryQerry by mutableStateOf("")
        internal var showProgress by mutableStateOf(true)
        internal var noResultText by mutableStateOf("")
    }

    private val vm = SearchResultsVM()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        for (info in PodcastSearcherRegistry.searchProviders) {
            Logd(TAG, "searchProvider: $info")
            if (info.searcher.javaClass.getName() == requireArguments().getString(ARG_SEARCHER)) {
                vm.searchProvider = info.searcher
                break
            }
        }
        if (vm.searchProvider == null) Logd(TAG,"Podcast searcher not found")
        vm.defaultText = requireArguments().getString(ARG_QUERY, "")
        search(vm.defaultText)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Logd(TAG, "fragment onCreateView")
        val composeView = ComposeView(requireContext()).apply { setContent { CustomTheme(requireContext()) { SearchResultsScreen() } } }
        return composeView
    }

    @Composable
    fun SearchResultsScreen() {
        val textColor = MaterialTheme.colorScheme.onSurface
        Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
            ConstraintLayout(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                val (gridView, progressBar, empty, txtvError, butRetry, powered) = createRefs()
                if (vm.showProgress) CircularProgressIndicator(progress = { 0.6f }, strokeWidth = 10.dp, modifier = Modifier.size(50.dp).constrainAs(progressBar) { centerTo(parent) })
                val lazyListState = rememberLazyListState()
                if (vm.searchResults.isNotEmpty()) LazyColumn(state = lazyListState, modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)
                    .constrainAs(gridView) {
                        top.linkTo(parent.top)
                        bottom.linkTo(parent.bottom)
                        start.linkTo(parent.start)
                    },
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(vm.searchResults.size) { index ->
                        val result = vm.searchResults[index]
                        val urlPrepared by remember { mutableStateOf(prepareUrl(result.feedUrl!!)) }
                        val sLog = remember { mutableStateOf(vm.feedLogs[urlPrepared]) }
//                    Logd(TAG, "result: ${result.feedUrl} ${feedLogs[urlPrepared]}")
                        OnlineFeedItem(activity = activity as MainActivity, result, sLog.value)
                    }
                }
                if (vm.searchResults.isEmpty()) Text(vm.noResultText, color = textColor, modifier = Modifier.constrainAs(empty) { centerTo(parent) })
                if (vm.errorText.isNotEmpty()) Text(vm.errorText, color = textColor, modifier = Modifier.constrainAs(txtvError) { centerTo(parent) })
                if (vm.retryQerry.isNotEmpty()) Button(modifier = Modifier.padding(16.dp).constrainAs(butRetry) { top.linkTo(txtvError.bottom) }, onClick = { search(vm.retryQerry) }) {
                    Text(stringResource(id = R.string.retry_label))
                }
                Text(getString(R.string.search_powered_by, vm.searchProvider!!.name), color = Color.Black, style = MaterialTheme.typography.labelSmall, modifier = Modifier.background(Color.LightGray)
                    .constrainAs(powered) {
                        bottom.linkTo(parent.bottom)
                        end.linkTo(parent.end)
                    })
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        TopAppBar(title = { SearchBarRow(R.string.search_podcast_hint, defaultText = requireArguments().getString(ARG_QUERY, "")) { queryText -> search(queryText) }},
            navigationIcon = { IconButton(onClick = { parentFragmentManager.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
        )
    }

    override fun onDestroy() {
        vm.searchResults.clear()
        super.onDestroy()
    }

    private var searchJob: Job? = null
    @SuppressLint("StringFormatMatches")
    private fun search(query: String) {
        if (query.isBlank()) return
        if (searchJob != null) {
            searchJob?.cancel()
            vm.searchResults.clear()
        }
        showOnlyProgressBar()
        searchJob = lifecycleScope.launch(Dispatchers.IO) {
            val feeds = getFeedList()
            fun feedId(r: PodcastSearchResult): Long {
                for (f in feeds) if (f.downloadUrl == r.feedUrl) return f.id
                return 0L
            }
            try {
                val result = vm.searchProvider?.search(query) ?: listOf()
                for (r in result) r.feedId = feedId(r)
                vm.searchResults.clear()
                vm.searchResults.addAll(result)
                withContext(Dispatchers.Main) {
                    vm.showProgress = false
                    vm.noResultText = getString(R.string.no_results_for_query, query)
                }
            } catch (e: Exception) { handleSearchError(e, query) }
        }.apply { invokeOnCompletion { searchJob = null } }
    }

    private fun handleSearchError(e: Throwable, query: String) {
        Logd(TAG, "exception: ${e.message}")
        vm.showProgress = false
        vm.errorText = e.toString()
        vm.retryQerry = query
    }

    private fun showOnlyProgressBar() {
        vm.errorText = ""
        vm.retryQerry = ""
        vm.showProgress = true
    }

//    private fun showInputMethod(view: View) {
//        val imm = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
//        imm.showSoftInput(view, 0)
//    }

    companion object {
        private val TAG: String = SearchResultsFragment::class.simpleName ?: "Anonymous"
        private const val ARG_SEARCHER = "searcher"
        private const val ARG_QUERY = "query"

        @JvmOverloads
        fun newInstance(searchProvider: Class<out PodcastSearcher?>, query: String? = null): SearchResultsFragment {
            val fragment = SearchResultsFragment()
            val arguments = Bundle()
            arguments.putString(ARG_SEARCHER, searchProvider.name)
            arguments.putString(ARG_QUERY, query)
            fragment.arguments = arguments
            return fragment
        }
    }
}
