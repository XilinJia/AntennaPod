package ac.mdiq.podcini.ui.dialog

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.DBWriter
import ac.mdiq.podcini.storage.model.feed.Feed
import ac.mdiq.podcini.util.Logd
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.*
import java.lang.Runnable

object RemoveFeedDialog {
    private const val TAG = "RemoveFeedDialog"

    fun show(context: Context, feed: Feed, callback: Runnable?) {
        val feeds = listOf(feed)
        val message = getMessageId(context, feeds)
        showDialog(context, feeds, message, callback)
    }

    fun show(context: Context, feeds: List<Feed>) {
        val message = getMessageId(context, feeds)
        showDialog(context, feeds, message, null)
    }

    private fun showDialog(context: Context, feeds: List<Feed>, message: String, callback: Runnable?) {
        val dialog: ConfirmationDialog = object : ConfirmationDialog(context, R.string.remove_feed_label, message) {
            @OptIn(UnstableApi::class) override fun onConfirmButtonPressed(clickedDialog: DialogInterface) {
                callback?.run()

                clickedDialog.dismiss()

                val progressDialog = ProgressDialog(context)
                progressDialog.setMessage(context.getString(R.string.feed_remover_msg))
                progressDialog.isIndeterminate = true
                progressDialog.setCancelable(false)
                progressDialog.show()

//                Completable.fromAction {
//                    for (feed in feeds) {
//                        DBWriter.deleteFeed(context, feed.id).get()
//                    }
//                }
//                    .subscribeOn(Schedulers.io())
//                    .observeOn(AndroidSchedulers.mainThread())
//                    .subscribe(
//                        {
//                            Logd(TAG, "Feed(s) deleted")
//                            progressDialog.dismiss()
//                        }, { error: Throwable? ->
//                            Log.e(TAG, Log.getStackTraceString(error))
//                            progressDialog.dismiss()
//                        })

                val scope = CoroutineScope(Dispatchers.Main)
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            for (feed in feeds) {
                                runBlocking { DBWriter.deleteFeed(context, feed.id).join() }
                            }
                        }
                        withContext(Dispatchers.Main) {
                            Logd(TAG, "Feed(s) deleted")
                            progressDialog.dismiss()
                        }
                    } catch (e: Throwable) {
                        Log.e(TAG, Log.getStackTraceString(e))
                        withContext(Dispatchers.Main) { progressDialog.dismiss() }
                    }
                }
            }
        }
        dialog.createNewDialog().show()
    }

    private fun getMessageId(context: Context, feeds: List<Feed>): String {
        return if (feeds.size == 1) {
            if (feeds[0].isLocalFeed) context.getString(R.string.feed_delete_confirmation_local_msg) + feeds[0].title
            else context.getString(R.string.feed_delete_confirmation_msg) + feeds[0].title
        } else context.getString(R.string.feed_delete_confirmation_msg_batch)

    }
}
