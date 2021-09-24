package org.moire.ultrasonic.subsonic

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.util.Locale
import java.util.regex.Pattern
import kotlin.collections.ArrayList
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.Share
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.BackgroundTask
import org.moire.ultrasonic.util.CancellationToken
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.FragmentBackgroundTask
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.ShareDetails
import org.moire.ultrasonic.util.TimeSpan
import org.moire.ultrasonic.util.TimeSpanPicker

/**
 * This class handles sharing items in the media library
 */
class ShareHandler(val context: Context) {
    private var shareDescription: EditText? = null
    private var timeSpanPicker: TimeSpanPicker? = null
    private var hideDialogCheckBox: CheckBox? = null
    private var noExpirationCheckBox: CheckBox? = null
    private var saveAsDefaultsCheckBox: CheckBox? = null
    private val pattern = Pattern.compile(":")

    fun createShare(
        fragment: Fragment,
        entries: List<MusicDirectory.Entry?>?,
        swipe: SwipeRefreshLayout?,
        cancellationToken: CancellationToken
    ) {
        val askForDetails = Settings.shouldAskForShareDetails
        val shareDetails = ShareDetails()
        shareDetails.Entries = entries
        if (askForDetails) {
            showDialog(fragment, shareDetails, swipe, cancellationToken)
        } else {
            shareDetails.Description = Settings.defaultShareDescription
            shareDetails.Expiration = TimeSpan.getCurrentTime().add(
                Settings.defaultShareExpirationInMillis
            ).totalMilliseconds
            share(fragment, shareDetails, swipe, cancellationToken)
        }
    }

    fun share(
        fragment: Fragment,
        shareDetails: ShareDetails,
        swipe: SwipeRefreshLayout?,
        cancellationToken: CancellationToken
    ) {
        val task: BackgroundTask<Share> = object : FragmentBackgroundTask<Share>(
            fragment.requireActivity(),
            true,
            swipe,
            cancellationToken
        ) {
            @Throws(Throwable::class)
            override fun doInBackground(): Share {
                val ids: MutableList<String> = ArrayList()
                if (shareDetails.Entries.isEmpty()) {
                    fragment.arguments?.getString(Constants.INTENT_EXTRA_NAME_ID)?.let {
                        ids.add(it)
                    }
                } else {
                    for ((id) in shareDetails.Entries) {
                        ids.add(id)
                    }
                }
                val musicService = getMusicService()
                var timeInMillis: Long = 0
                if (shareDetails.Expiration != 0L) {
                    timeInMillis = shareDetails.Expiration
                }
                val shares =
                    musicService.createShare(ids, shareDetails.Description, timeInMillis)
                return shares[0]
            }

            override fun done(result: Share) {
                val intent = Intent(Intent.ACTION_SEND)
                intent.type = "text/plain"
                intent.putExtra(
                    Intent.EXTRA_TEXT,
                    String.format(Locale.ROOT, "%s\n\n%s", Settings.shareGreeting, result.url)
                )
                fragment.activity?.startActivity(
                    Intent.createChooser(
                        intent,
                        context.resources.getString(R.string.share_via)
                    )
                )
            }
        }
        task.execute()
    }

    private fun showDialog(
        fragment: Fragment,
        shareDetails: ShareDetails,
        swipe: SwipeRefreshLayout?,
        cancellationToken: CancellationToken
    ) {
        val layout = LayoutInflater.from(fragment.context).inflate(R.layout.share_details, null)

        if (layout != null) {
            shareDescription = layout.findViewById<View>(R.id.share_description) as EditText
            hideDialogCheckBox = layout.findViewById<View>(R.id.hide_dialog) as CheckBox
            noExpirationCheckBox = layout.findViewById<View>(
                R.id.timeSpanDisableCheckBox
            ) as CheckBox
            saveAsDefaultsCheckBox = layout.findViewById<View>(R.id.save_as_defaults) as CheckBox
            timeSpanPicker = layout.findViewById<View>(R.id.date_picker) as TimeSpanPicker
        }
        val builder = AlertDialog.Builder(fragment.context)
        builder.setTitle(R.string.share_set_share_options)
        builder.setPositiveButton(R.string.common_save) { _, _ ->
            if (!noExpirationCheckBox!!.isChecked) {
                val timeSpan: TimeSpan = timeSpanPicker!!.timeSpan
                val now = TimeSpan.getCurrentTime()
                shareDetails.Expiration = now.add(timeSpan).totalMilliseconds
            }
            shareDetails.Description = shareDescription!!.text.toString()
            if (hideDialogCheckBox!!.isChecked) {
                Settings.shouldAskForShareDetails = false
            }
            if (saveAsDefaultsCheckBox!!.isChecked) {
                val timeSpanType: String = timeSpanPicker!!.timeSpanType
                val timeSpanAmount: Int = timeSpanPicker!!.timeSpanAmount
                Settings.defaultShareExpiration =
                    if (!noExpirationCheckBox!!.isChecked && timeSpanAmount > 0)
                        String.format("%d:%s", timeSpanAmount, timeSpanType) else ""

                Settings.defaultShareDescription = shareDetails.Description
            }
            share(fragment, shareDetails, swipe, cancellationToken)
        }
        builder.setNegativeButton(R.string.common_cancel) { dialog, _ ->
            dialog.cancel()
        }
        builder.setView(layout)
        builder.setCancelable(true)
        timeSpanPicker!!.setTimeSpanDisableText(context.resources.getString(R.string.no_expiration))
        noExpirationCheckBox!!.setOnCheckedChangeListener {
            _,
            b ->
            timeSpanPicker!!.isEnabled = !b
        }
        val defaultDescription = Settings.defaultShareDescription
        val timeSpan = Settings.defaultShareExpiration
        val split = pattern.split(timeSpan)
        if (split.size == 2) {
            val timeSpanAmount = split[0].toInt()
            val timeSpanType = split[1]
            if (timeSpanAmount > 0) {
                noExpirationCheckBox!!.isChecked = false
                timeSpanPicker!!.isEnabled = true
                timeSpanPicker!!.setTimeSpanAmount(timeSpanAmount.toString())
                timeSpanPicker!!.setTimeSpanType(timeSpanType)
            } else {
                noExpirationCheckBox!!.isChecked = true
                timeSpanPicker!!.isEnabled = false
            }
        } else {
            noExpirationCheckBox!!.isChecked = true
            timeSpanPicker!!.isEnabled = false
        }
        shareDescription!!.setText(defaultDescription)
        builder.create()
        builder.show()
    }
}
