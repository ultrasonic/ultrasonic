package org.moire.ultrasonic.fragment

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ListView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.android.viewmodel.ext.android.viewModel
import org.moire.ultrasonic.R
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.fragment.EditServerFragment.Companion.EDIT_SERVER_INTENT_INDEX
import org.moire.ultrasonic.service.MediaPlayerController
import org.moire.ultrasonic.util.Util
import timber.log.Timber

class ServerSelectorFragment: Fragment() {
    companion object {
        const val SERVER_SELECTOR_MANAGE_MODE = "manageMode"
    }

    private var listView: ListView? = null
    private val serverSettingsModel: ServerSettingsModel by viewModel()
    private val service: MediaPlayerController by inject()
    private val activeServerProvider: ActiveServerProvider by inject()
    private var serverRowAdapter: ServerRowAdapter? = null

    @Override
    override fun onCreate(savedInstanceState: Bundle?) {
        Util.applyTheme(this.context)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.server_selector, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val manageMode = arguments?.getBoolean(
            SERVER_SELECTOR_MANAGE_MODE,
            false
        ) ?: false
        if (manageMode) {
            FragmentTitle.setTitle(this, R.string.settings_server_manage_servers)
        } else {
            FragmentTitle.setTitle(this, R.string.server_selector_label)
        }

        listView = view.findViewById(R.id.server_list)
        serverRowAdapter = ServerRowAdapter(
            view.context,
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
                findNavController().navigateUp()
            }
        }

        val fab = view.findViewById<FloatingActionButton>(R.id.server_add_fab)
        fab.setOnClickListener {
            editServer(-1)
        }
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
                    service.isJukeboxEnabled =
                        activeServerProvider.getActiveServer().jukeboxByDefault
                }
            }
        }
        Timber.i("Active server was set to: $index")
    }

    /**
     * This Callback handles the deletion of a Server Setting
     */
    private fun onServerDeleted(index: Int) {
        AlertDialog.Builder(context)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle(R.string.server_menu_delete)
            .setMessage(R.string.server_selector_delete_confirmation)
            .setPositiveButton(R.string.common_delete) { dialog, _ ->
                dialog.dismiss()

                val activeServerIndex = activeServerProvider.getActiveServer().index
                // If the currently active server is deleted, go offline
                if (index == activeServerIndex) setActiveServer(-1)

                serverSettingsModel.deleteItem(index)
                Timber.i("Server deleted: $index")
            }
            .setNegativeButton(R.string.common_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Starts the Edit Server Fragment to edit the details of a server
     */
    private fun editServer(index: Int) {
        val bundle = Bundle()
        bundle.putInt(EDIT_SERVER_INTENT_INDEX, index)
        findNavController().navigate(R.id.serverSelectorToEditServer, bundle)
    }
}