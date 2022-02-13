/*
 * AboutFragment.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import java.util.Locale
import org.moire.ultrasonic.R
import org.moire.ultrasonic.fragment.FragmentTitle.Companion.setTitle
import org.moire.ultrasonic.util.Util.applyTheme
import org.moire.ultrasonic.util.Util.getVersionName

/**
 * Displays the About page
 */
class AboutFragment : Fragment() {
    private var titleText: TextView? = null
    private var webPageButton: Button? = null
    private var reportBugButton: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme(this.context)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.help, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        titleText = view.findViewById(R.id.help_title)
        webPageButton = view.findViewById(R.id.help_webpage)
        reportBugButton = view.findViewById(R.id.help_report)

        val versionName = getVersionName(requireContext())
        val title = String.format(
            Locale.getDefault(),
            "%s (%s)",
            getString(R.string.common_appname),
            versionName
        )

        setTitle(this@AboutFragment, title)
        titleText?.text = title

        webPageButton?.setOnClickListener {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.about_webpage_url)))
            )
        }

        reportBugButton?.setOnClickListener {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.about_report_url)))
            )
        }
    }
}
