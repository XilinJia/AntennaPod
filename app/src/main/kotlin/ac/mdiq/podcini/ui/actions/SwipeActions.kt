package ac.mdiq.podcini.ui.actions

//import ac.mdiq.podcini.ui.dialog.SwipeActionsDialog
import ac.mdiq.podcini.R
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.storage.database.Episodes.deleteMediaSync
import ac.mdiq.podcini.storage.database.Episodes.setPlayState
import ac.mdiq.podcini.storage.database.Episodes.shouldDeleteRemoveFromQueue
import ac.mdiq.podcini.storage.database.Feeds.shouldAutoDeleteItem
import ac.mdiq.podcini.storage.database.Queues.addToQueue
import ac.mdiq.podcini.storage.database.Queues.removeFromQueue
import ac.mdiq.podcini.storage.database.Queues.removeFromQueueSync
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeFilter
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.model.PlayState
import ac.mdiq.podcini.storage.utils.EpisodeUtil
import ac.mdiq.podcini.ui.actions.SwipeAction.ActionTypes
import ac.mdiq.podcini.ui.actions.SwipeAction.ActionTypes.NO_ACTION
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.compose.ChooseRatingDialog
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.compose.PlayStateDialog
import ac.mdiq.podcini.ui.fragment.*
import ac.mdiq.podcini.ui.utils.LocalDeleteModal.deleteEpisodesWarnLocal
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import android.content.Context
import android.content.SharedPreferences
import android.util.TypedValue
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.util.UnstableApi
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.*
import kotlin.math.ceil

open class SwipeActions(private val fragment: Fragment, private val tag: String) : DefaultLifecycleObserver {

    @set:JvmName("setFilterProperty")
    var filter: EpisodeFilter? = null
    var actions: Actions

    init {
        actions = getPrefs(tag)
    }

    override fun onStart(owner: LifecycleOwner) {
        actions = getPrefs(tag)
    }

//    override fun onStop(owner: LifecycleOwner) {
////        actions = null
//    }

    @JvmName("setFilterFunction")
    fun setFilter(filter: EpisodeFilter?) {
        this.filter = filter
    }

    fun showDialog() {
//        SwipeActionsDialog(fragment.requireContext(), tag).show(object : SwipeActionsDialog.Callback {
//            override fun onCall() {
//                actions = getPrefs(tag)
//                EventFlow.postEvent(FlowEvent.SwipeActionsChangedEvent())
//            }
//        })
        val composeView = ComposeView(fragment.requireContext()).apply {
            setContent {
                val showDialog = remember { mutableStateOf(true) }
                CustomTheme(fragment.requireContext()) {
                    SwipeActionsDialog(this@SwipeActions.tag, onDismissRequest = {
                        showDialog.value = false
                        (fragment.view as? ViewGroup)?.removeView(this@apply)
                    }) {
                        actions = getPrefs(this@SwipeActions.tag)
                        EventFlow.postEvent(FlowEvent.SwipeActionsChangedEvent())
                    }
                }
            }
        }
        (fragment.view as? ViewGroup)?.addView(composeView)
    }

    class Actions(prefs: String?) {
        @JvmField
        var right: MutableList<SwipeAction> = mutableListOf(swipeActions[0], swipeActions[0])
        @JvmField
        var left: MutableList<SwipeAction> = mutableListOf(swipeActions[0], swipeActions[0])

        init {
            val actions = prefs!!.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (actions.size == 2) {
                val rActs = swipeActions.filter { a: SwipeAction -> a.getId().equals(actions[0]) }
                this.right[0] = if (rActs.isEmpty()) swipeActions[0] else rActs[0]
                val lActs = swipeActions.filter { a: SwipeAction -> a.getId().equals(actions[1]) }
                this.left[0] = if (lActs.isEmpty()) swipeActions[0] else lActs[0]
            }
        }
    }

    class AddToQueueSwipeAction : SwipeAction {
        override fun getId(): String {
            return ActionTypes.ADD_TO_QUEUE.name
        }

        override fun getActionIcon(): Int {
            return R.drawable.ic_playlist_play
        }

        override fun getActionColor(): Int {
            return androidx.appcompat.R.attr.colorAccent
        }

        override fun getTitle(context: Context): String {
            return context.getString(R.string.add_to_queue_label)
        }

        @OptIn(UnstableApi::class)
        override fun performAction(item: Episode, fragment: Fragment, filter: EpisodeFilter) {
            addToQueue(item)
        }

        override fun willRemove(filter: EpisodeFilter, item: Episode): Boolean {
            return filter.showQueued || filter.showNew
        }
    }

    class ComboSwipeAction : SwipeAction {
        override fun getId(): String {
            return ActionTypes.COMBO.name
        }

        override fun getActionIcon(): Int {
            return R.drawable.baseline_category_24
        }

        override fun getActionColor(): Int {
            return androidx.appcompat.R.attr.colorAccent
        }

        override fun getTitle(context: Context): String {
            return context.getString(R.string.add_to_queue_label)
        }

        @OptIn(UnstableApi::class)
        override fun performAction(item: Episode, fragment: Fragment, filter: EpisodeFilter) {
            val composeView = ComposeView(fragment.requireContext()).apply {
                setContent {
                    var showDialog by remember { mutableStateOf(true) }
                    CustomTheme(fragment.requireContext()) {
                        if (showDialog) Dialog(onDismissRequest = {
                            showDialog = false
                            (fragment.view as? ViewGroup)?.removeView(this@apply)
                        }) {
                            val context = LocalContext.current
                            Surface(shape = RoundedCornerShape(16.dp)) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    for (action in swipeActions) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp).clickable {
                                            action.performAction(item, fragment, filter)
                                            showDialog = false
                                            (fragment.view as? ViewGroup)?.removeView(this@apply)
                                        }) {
                                            val colorAccent = remember {
                                                val typedValue = TypedValue()
                                                context.theme.resolveAttribute(action.getActionColor(), typedValue, true)
                                                Color(typedValue.data)
                                            }
                                            Icon(imageVector = ImageVector.vectorResource(id = action.getActionIcon()),  tint = colorAccent, contentDescription = action.getTitle(context))
                                            Text(action.getTitle(context), Modifier.padding(start = 4.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            (fragment.view as? ViewGroup)?.addView(composeView)
        }

        override fun willRemove(filter: EpisodeFilter, item: Episode): Boolean {
            return filter.showQueued || filter.showNew
        }
    }

    class DeleteSwipeAction : SwipeAction {
        override fun getId(): String {
            return ActionTypes.DELETE.name
        }

        override fun getActionIcon(): Int {
            return R.drawable.ic_delete
        }

        override fun getActionColor(): Int {
            return R.attr.icon_red
        }

        override fun getTitle(context: Context): String {
            return context.getString(R.string.delete_episode_label)
        }

        @UnstableApi
        override fun performAction(item: Episode, fragment: Fragment, filter: EpisodeFilter) {
            if (!item.isDownloaded && item.feed?.isLocalFeed != true) return
            deleteEpisodesWarnLocal(fragment.requireContext(), listOf(item))
        }

        override fun willRemove(filter: EpisodeFilter, item: Episode): Boolean {
            return filter.showDownloaded && (item.isDownloaded || item.feed?.isLocalFeed == true)
        }
    }

    class SetRatingSwipeAction : SwipeAction {
        override fun getId(): String {
            return ActionTypes.MARK_FAV.name
        }

        override fun getActionIcon(): Int {
            return R.drawable.ic_star
        }

        override fun getActionColor(): Int {
            return R.attr.icon_yellow
        }

        override fun getTitle(context: Context): String {
            return context.getString(R.string.switch_rating_label)
        }

        @OptIn(UnstableApi::class)
        override fun performAction(item: Episode, fragment: Fragment, filter: EpisodeFilter) {
            var showChooseRatingDialog by mutableStateOf(true)
            val composeView = ComposeView(fragment.requireContext()).apply {
                setContent {
                    CustomTheme(fragment.requireContext()) {
                        if (showChooseRatingDialog) ChooseRatingDialog(listOf(item)) {
                            showChooseRatingDialog = false
                            (fragment.view as? ViewGroup)?.removeView(this@apply)
                        }
                    }
                }
            }
            (fragment.view as? ViewGroup)?.addView(composeView)
        }

        override fun willRemove(filter: EpisodeFilter, item: Episode): Boolean {
            return filter.showIsFavorite || filter.showNotFavorite
        }
    }

    class NoActionSwipeAction : SwipeAction {
        override fun getId(): String {
            return NO_ACTION.name
        }

        override fun getActionIcon(): Int {
            return R.drawable.ic_questionmark
        }

        override fun getActionColor(): Int {
            return R.attr.icon_red
        }

        override fun getTitle(context: Context): String {
            return context.getString(R.string.no_action_label)
        }

        @UnstableApi
        override fun performAction(item: Episode, fragment: Fragment, filter: EpisodeFilter) {}

        override fun willRemove(filter: EpisodeFilter, item: Episode): Boolean {
            return false
        }
    }

    class RemoveFromHistorySwipeAction : SwipeAction {
        val TAG = this::class.simpleName ?: "Anonymous"

        override fun getId(): String {
            return ActionTypes.REMOVE_FROM_HISTORY.name
        }

        override fun getActionIcon(): Int {
            return R.drawable.ic_history_remove
        }

        override fun getActionColor(): Int {
            return R.attr.icon_purple
        }

        override fun getTitle(context: Context): String {
            return context.getString(R.string.remove_history_label)
        }

        @OptIn(UnstableApi::class)
        override fun performAction(item: Episode, fragment: Fragment, filter: EpisodeFilter) {
            val playbackCompletionDate: Date? = item.media?.playbackCompletionDate
            val lastPlayedDate = item.media?.lastPlayedTime
            setHistoryDates(item)

            (fragment.requireActivity() as MainActivity)
                .showSnackbarAbovePlayer(R.string.removed_history_label, Snackbar.LENGTH_LONG)
                .setAction(fragment.getString(R.string.undo)) {
                    if (playbackCompletionDate != null) setHistoryDates(item, lastPlayedDate?:0, playbackCompletionDate) }
        }

        override fun willRemove(filter: EpisodeFilter, item: Episode): Boolean {
            return true
        }

        private fun setHistoryDates(episode: Episode, lastPlayed: Long = 0, completed: Date = Date(0)) {
            runOnIOScope {
                val episode_ = realm.query(Episode::class).query("id == $0", episode.id).first().find()
                if (episode_ != null) {
                    upsert(episode_) {
                        it.media?.lastPlayedTime = lastPlayed
                        it.media?.playbackCompletionDate = completed
                    }
                    EventFlow.postEvent(FlowEvent.HistoryEvent())
                }
            }
        }
    }

    class RemoveFromQueueSwipeAction : SwipeAction {
        override fun getId(): String {
            return ActionTypes.REMOVE_FROM_QUEUE.name
        }

        override fun getActionIcon(): Int {
            return R.drawable.ic_playlist_remove
        }

        override fun getActionColor(): Int {
            return androidx.appcompat.R.attr.colorAccent
        }

        override fun getTitle(context: Context): String {
            return context.getString(R.string.remove_from_queue_label)
        }

        @OptIn(UnstableApi::class)
        override fun performAction(item: Episode, fragment: Fragment, filter: EpisodeFilter) {
            val position: Int = curQueue.episodes.indexOf(item)
            removeFromQueue(item)
            if (willRemove(filter, item)) {
                (fragment.requireActivity() as MainActivity).showSnackbarAbovePlayer(fragment.resources.getQuantityString(R.plurals.removed_from_queue_batch_label, 1, 1), Snackbar.LENGTH_LONG)
                    .setAction(fragment.getString(R.string.undo)) {
                        addToQueueAt(item, position)
                    }
            }
        }

        override fun willRemove(filter: EpisodeFilter, item: Episode): Boolean {
            return filter.showQueued || filter.showNotQueued
        }

        /**
         * Inserts a Episode in the queue at the specified index. The 'read'-attribute of the Episode will be set to
         * true. If the Episode is already in the queue, the queue will not be modified.
         * @param episode                the Episode that should be added to the queue.
         * @param index               Destination index. Must be in range 0..queue.size()
         * @throws IndexOutOfBoundsException if index < 0 || index >= queue.size()
         */
        @UnstableApi
        fun addToQueueAt(episode: Episode, index: Int) : Job {
            return runOnIOScope {
                if (curQueue.episodeIds.contains(episode.id)) return@runOnIOScope
                if (episode.isNew) setPlayState(PlayState.UNPLAYED.code, false, episode)
                curQueue = upsert(curQueue) {
                    it.episodeIds.add(index, episode.id)
                    it.update()
                }
//            curQueue.episodes.add(index, episode)
                EventFlow.postEvent(FlowEvent.QueueEvent.added(episode, index))
//            if (performAutoDownload) autodownloadEpisodeMedia(context)
            }
        }
    }

    class StartDownloadSwipeAction : SwipeAction {
        override fun getId(): String {
            return ActionTypes.START_DOWNLOAD.name
        }

        override fun getActionIcon(): Int {
            return R.drawable.ic_download
        }

        override fun getActionColor(): Int {
            return R.attr.icon_green
        }

        override fun getTitle(context: Context): String {
            return context.getString(R.string.download_label)
        }

        override fun performAction(item: Episode, fragment: Fragment, filter: EpisodeFilter) {
            if (!item.isDownloaded && item.feed != null && !item.feed!!.isLocalFeed) {
                DownloadActionButton(item).onClick(fragment.requireContext())
            }
        }

        override fun willRemove(filter: EpisodeFilter, item: Episode): Boolean {
            return false
        }
    }

    class SetPlaybackStateSwipeAction : SwipeAction {
        override fun getId(): String {
            return ActionTypes.SET_PLAY_STATE.name
        }

        override fun getActionIcon(): Int {
            return R.drawable.ic_mark_played
        }

        override fun getActionColor(): Int {
            return R.attr.icon_gray
        }

        override fun getTitle(context: Context): String {
            return context.getString(R.string.set_play_state_label)
        }

        override fun performAction(item: Episode, fragment: Fragment, filter: EpisodeFilter) {
            var showPlayStateDialog by mutableStateOf(true)
            val composeView = ComposeView(fragment.requireContext()).apply {
                setContent {
                    CustomTheme(fragment.requireContext()) {
                        if (showPlayStateDialog) PlayStateDialog(listOf(item)) {
                            showPlayStateDialog = false
                            (fragment.view as? ViewGroup)?.removeView(this@apply)
                        }
                    }
                }
            }
            (fragment.view as? ViewGroup)?.addView(composeView)
        }

        private fun delayedExecution(item: Episode, fragment: Fragment, duration: Float) = runBlocking {
            delay(ceil((duration * 1.05f).toDouble()).toLong())
            val media: EpisodeMedia? = item.media
            val shouldAutoDelete = if (item.feed == null) false else shouldAutoDeleteItem(item.feed!!)
            if (media != null && EpisodeUtil.hasAlmostEnded(media) && shouldAutoDelete) {
//                deleteMediaOfEpisode(fragment.requireContext(), item)
                var item = deleteMediaSync(fragment.requireContext(), item)
                if (shouldDeleteRemoveFromQueue()) removeFromQueueSync(null, item)   }
        }

        override fun willRemove(filter: EpisodeFilter, item: Episode): Boolean {
            return if (item.playState == PlayState.NEW.code) filter.showPlayed || filter.showNew
            else filter.showUnplayed || filter.showPlayed || filter.showNew
        }
    }

    companion object {
        private const val SWIPE_ACTIONS_PREF_NAME: String = "SwipeActionsPrefs"
        private const val KEY_PREFIX_SWIPEACTIONS: String = "PrefSwipeActions"
        private const val KEY_PREFIX_NO_ACTION: String = "PrefNoSwipeAction"

        var prefs: SharedPreferences? = null
        fun getSharedPrefs(context: Context) {
            if (prefs == null) prefs = context.getSharedPreferences(SWIPE_ACTIONS_PREF_NAME, Context.MODE_PRIVATE)
        }

        @JvmField
        val swipeActions: List<SwipeAction> = listOf(
            NoActionSwipeAction(), ComboSwipeAction(), AddToQueueSwipeAction(),
            StartDownloadSwipeAction(), SetRatingSwipeAction(),
            SetPlaybackStateSwipeAction(), RemoveFromQueueSwipeAction(),
            DeleteSwipeAction(), RemoveFromHistorySwipeAction())

        private fun getPrefs(tag: String, defaultActions: String): Actions {
            val prefsString = prefs!!.getString(KEY_PREFIX_SWIPEACTIONS + tag, defaultActions)
            return Actions(prefsString)
        }

        fun getPrefs(tag: String): Actions {
            return getPrefs(tag, "")
        }

        @OptIn(UnstableApi::class) @JvmStatic
        fun getPrefsWithDefaults(tag: String): Actions {
            val defaultActions = when (tag) {
                QueuesFragment.TAG -> "${NO_ACTION.name},${NO_ACTION.name}"
                DownloadsFragment.TAG -> "${NO_ACTION.name},${NO_ACTION.name}"
                HistoryFragment.TAG -> "${NO_ACTION.name},${NO_ACTION.name}"
                AllEpisodesFragment.TAG -> "${NO_ACTION.name},${NO_ACTION.name}"
                else -> "${NO_ACTION.name},${NO_ACTION.name}"
            }
            return getPrefs(tag, defaultActions)
        }

//        fun isSwipeActionEnabled(tag: String): Boolean {
//            return prefs!!.getBoolean(KEY_PREFIX_NO_ACTION + tag, true)
//        }

        fun showSettingDialog(fragment: Fragment, tag: String) {
            val composeView = ComposeView(fragment.requireContext()).apply {
                setContent {
                    val showDialog = remember { mutableStateOf(true) }
                    CustomTheme(fragment.requireContext()) {
                        SwipeActionsDialog(tag, onDismissRequest = {
                            showDialog.value = false
                            (fragment.view as? ViewGroup)?.removeView(this@apply)
                        }) {}
                    }
                }
            }
            (fragment.view as? ViewGroup)?.addView(composeView)
        }

        @Composable
        fun SwipeActionsDialog(tag: String, onDismissRequest: () -> Unit, callback: ()->Unit) {
            val context = LocalContext.current
            val textColor = MaterialTheme.colorScheme.onSurface

            val actions = getPrefsWithDefaults(tag)
            val leftAction = remember { mutableStateOf(actions.left) }
            val rightAction = remember { mutableStateOf(actions.right) }
            var keys = swipeActions

            fun savePrefs(tag: String, right: String?, left: String?) {
                getSharedPrefs(context)
                prefs!!.edit().putString(KEY_PREFIX_SWIPEACTIONS + tag, "$right,$left").apply()
            }
            fun saveActionsEnabledPrefs(enabled: Boolean) {
                getSharedPrefs(context)
                prefs!!.edit().putBoolean(KEY_PREFIX_NO_ACTION + tag, enabled).apply()
            }

            var direction by remember { mutableIntStateOf(0) }
            var showPickerDialog by remember { mutableStateOf(false) }
            if (showPickerDialog) {
                Dialog(onDismissRequest = { showPickerDialog = false }) {
                    Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp)) {
                        LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.padding(16.dp)) {
                            items(keys.size) { index ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp).clickable {
                                    when (direction) {
                                        -1 -> leftAction.value[0] = keys[index]
                                        1 -> rightAction.value[0] = keys[index]
                                        else -> {}
                                    }
                                    showPickerDialog = false
                                }) {
                                    Icon(imageVector = ImageVector.vectorResource(keys[index].getActionIcon()), tint = textColor, contentDescription = null, modifier = Modifier.width(35.dp).height(35.dp))
                                    Text(keys[index].getTitle(context), color = textColor, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            }

            Dialog(onDismissRequest = { onDismissRequest() }) {
                var forFragment = ""
                when (tag) {
                    AllEpisodesFragment.TAG -> {
                        forFragment = stringResource(R.string.episodes_label)
                        keys = keys.filter { a: SwipeAction -> !a.getId().equals(ActionTypes.REMOVE_FROM_HISTORY.name) }
                    }
                    DownloadsFragment.TAG -> {
                        forFragment = stringResource(R.string.downloads_label)
                        keys = keys.filter { a: SwipeAction ->
                            (!a.getId().equals(ActionTypes.REMOVE_FROM_HISTORY.name) && !a.getId().equals(ActionTypes.START_DOWNLOAD.name)) }
                    }
                    FeedEpisodesFragment.TAG -> {
                        forFragment = stringResource(R.string.subscription)
                        keys = keys.filter { a: SwipeAction -> !a.getId().equals(ActionTypes.REMOVE_FROM_HISTORY.name) }
                    }
                    QueuesFragment.TAG -> {
                        forFragment = stringResource(R.string.queue_label)
//                keys = Stream.of(keys).filter { a: SwipeAction ->
//                    (!a.getId().equals(SwipeAction.ADD_TO_QUEUE) && !a.getId().equals(SwipeAction.REMOVE_FROM_HISTORY)) }.toList()
                        keys = keys.filter { a: SwipeAction -> (!a.getId().equals(ActionTypes.REMOVE_FROM_HISTORY.name)) }
                    }
                    HistoryFragment.TAG -> {
                        forFragment = stringResource(R.string.playback_history_label)
                        keys = keys.toList()
                    }
                    else -> {}
                }
                if (tag != QueuesFragment.TAG) keys = keys.filter { a: SwipeAction -> !a.getId().equals(ActionTypes.REMOVE_FROM_QUEUE.name) }
                Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text(stringResource(R.string.swipeactions_label) + " - " + forFragment)
                        Text(stringResource(R.string.swipe_left))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp, end = 10.dp)) {
                            Spacer(Modifier.weight(0.1f))
                            Icon(imageVector = ImageVector.vectorResource(leftAction.value[0].getActionIcon()), tint = textColor, contentDescription = null, modifier = Modifier.width(35.dp).height(35.dp)
                                .clickable(onClick = {
                                    direction = -1
                                    showPickerDialog = true
                                })
                            )
                            Spacer(Modifier.weight(0.1f))
                            Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_arrow_left_alt_24), tint = textColor, contentDescription = "right_arrow", modifier = Modifier.width(50.dp).height(35.dp))
                            Spacer(Modifier.weight(0.5f))
                        }
                        Text(stringResource(R.string.swipe_right))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp, end = 10.dp)) {
                            Spacer(Modifier.weight(0.5f))
                            Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_arrow_right_alt_24), tint = textColor, contentDescription = "right_arrow", modifier = Modifier.width(50.dp).height(35.dp))
                            Spacer(Modifier.weight(0.1f))
                            Icon(imageVector = ImageVector.vectorResource(rightAction.value[0].getActionIcon()), tint = textColor, contentDescription = null, modifier = Modifier.width(35.dp).height(35.dp)
                                .clickable(onClick = {
                                    direction = 1
                                    showPickerDialog = true
                                })
                            )
                            Spacer(Modifier.weight(0.1f))
                        }
                        Button(onClick = {
                            savePrefs(tag, rightAction.value[0].getId(), leftAction.value[0].getId())
                            saveActionsEnabledPrefs(true)
                            EventFlow.postEvent(FlowEvent.SwipeActionsChangedEvent())
                            callback()
                            onDismissRequest()
                        }) {
                            Text("Confirm")
                        }
                    }
                }
            }
        }
    }
}