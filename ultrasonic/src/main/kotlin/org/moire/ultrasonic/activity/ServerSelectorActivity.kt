package org.moire.ultrasonic.activity

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.AdapterView
import android.widget.ListView
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.android.viewmodel.ext.android.viewModel
import org.moire.ultrasonic.R
import org.moire.ultrasonic.activity.EditServerActivity.Companion.EDIT_SERVER_INTENT_INDEX
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.service.MediaPlayerController
import org.moire.ultrasonic.util.Util

/**
 * This Activity can be used to display all the configured Server Setting items.
 * It also contains a FAB to add a new server.
 * It has a Manage Mode and a Select Mode. In Select Mode, clicking the List Items will select
 * the server, and a server can be edited using the context menu. In Manage Mode the default
 * action when a List Item is clicked is to edit the server.
 */
internal class ServerSelectorActivity : AppCompatActivity() {

    companion object {
        private val TAG = ServerSelectorActivity::class.simpleName
        const val SERVER_SELECTOR_MANAGE_MODE = "manageMode"
    }

    private var listView: ListView? = null
    private val serverSettingsModel: ServerSettingsModel by viewModel()
    private val service: MediaPlayerController by inject()
    private val activeServerProvider: ActiveServerProvider by inject()
    private var serverRowAdapter: ServerRowAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        applyTheme()
        if (savedInstanceState == null) configureActionBar()

        setContentView(R.layout.server_selector)

        val manageMode = intent.getBooleanExtra(SERVER_SELECTOR_MANAGE_MODE, false)
        if (manageMode) {
            setTitle(R.string.settings_server_manage_servers)
        } else {
            setTitle(R.string.server_selector_label)
        }

        listView = findViewById(R.id.server_list)
        serverRowAdapter = ServerRowAdapter(
            this,
            arrayOf(),
            serverSettingsModel,
            activeServerProvider,
            manageMode,
            {
                i ->
                onServerDeleted(i)
            },
            {
                i ->
                editServer(i)
            }
        )

        listView?.adapter = serverRowAdapter

        listView?.onItemClickListener = AdapterView.OnItemClickListener {
            _, _, position, _ ->
            if (manageMode) {
                editServer(position + 1)
            } else {
                setActiveServer(position)
                finish()
            }
        }

        val fab = findViewById<FloatingActionButton>(R.id.server_add_fab)
        fab.setOnClickListener {
            editServer(-1)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        val serverList = serverSettingsModel.getServerList()
        serverList.observe(
            this,
            Observer { t ->
                serverRowAdapter!!.setData(t.toTypedArray())
            }
        )
    }
    private fun applyTheme() {
        val theme = Util.getTheme(this)
        if (
            "dark".equals(theme, ignoreCase = true) ||
            "fullscreen".equals(theme, ignoreCase = true)
        ) {
            setTheme(R.style.UltraSonicTheme)
        } else if (
            "light".equals(theme, ignoreCase = true) ||
            "fullscreenlight".equals(theme, ignoreCase = true)
        ) {
            setTheme(R.style.UltraSonicTheme_Light)
        }
    }

    private fun configureActionBar() {
        val actionBar: ActionBar? = supportActionBar
        if (actionBar != null) {
            actionBar.setDisplayShowHomeEnabled(true)
            actionBar.setDisplayHomeAsUpEnabled(true)
        }
    }

    /**
     * Sets the active server when a list item is clicked
     */
    private fun setActiveServer(index: Int) {
        // TODO this is still a blocking call - we shouldn't leave this activity before the active server is updated.
        // Maybe this can be refactored by using LiveData, or this can be made more user friendly with a ProgressDialog
        runBlocking {
            withContext(Dispatchers.IO) {
                if (activeServerProvider.getActiveServer().index != index) {
                    service.clearIncomplete()
                    activeServerProvider.setActiveServerByIndex(index)
                }
                service.isJukeboxEnabled = activeServerProvider.getActiveServer().jukeboxByDefault
            }
        }
        Log.i(TAG, "Active server was set to: $index")
    }

    /**
     * This Callback handles the deletion of a Server Setting
     */
    private fun onServerDeleted(index: Int) {
        AlertDialog.Builder(this)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle(R.string.server_menu_delete)
            .setMessage(R.string.server_selector_delete_confirmation)
            .setPositiveButton(R.string.common_delete) { dialog, _ ->
                dialog.dismiss()

                val activeServerIndex = activeServerProvider.getActiveServer().index
                // If the currently active server is deleted, go offline
                if (index == activeServerIndex) setActiveServer(-1)

                serverSettingsModel.deleteItem(index)
                Log.i(TAG, "Server deleted: $index")
            }
            .setNegativeButton(R.string.common_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Starts the Edit Server Activity to edit the details of a server
     */
    private fun editServer(index: Int) {
        val intent = Intent(this, EditServerActivity::class.java)
        intent.putExtra(EDIT_SERVER_INTENT_INDEX, index)
        startActivityForResult(intent, 0)
    }
}
