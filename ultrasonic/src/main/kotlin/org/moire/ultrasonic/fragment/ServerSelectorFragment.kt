package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ListView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.moire.ultrasonic.R
import org.moire.ultrasonic.adapters.ServerRowAdapter
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.ServerSetting
import org.moire.ultrasonic.fragment.EditServerFragment.Companion.EDIT_SERVER_INTENT_INDEX
import org.moire.ultrasonic.model.ServerSettingsModel
import org.moire.ultrasonic.service.MediaPlayerController
import org.moire.ultrasonic.util.ErrorDialog
import org.moire.ultrasonic.util.Util
import timber.log.Timber

/**
 * Displays the list of configured servers, they can be selected or edited
 *
 * TODO: Manage mode is unused. Remove it...
 */
class ServerSelectorFragment : Fragment() {
    companion object {
        const val SERVER_SELECTOR_MANAGE_MODE = "manageMode"
    }

    private var listView: ListView? = null
    private val serverSettingsModel: ServerSettingsModel by viewModel()
    private val controller: MediaPlayerController by inject()
    private val activeServerProvider: ActiveServerProvider by inject()
    private var serverRowAdapter: ServerRowAdapter? = null

    @Override
    override fun onCreate(savedInstanceState: Bundle?) {
        Util.applyTheme(this.context)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
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
            ::deleteServerById,
            ::editServerByIndex
        )

        listView?.adapter = serverRowAdapter

        listView?.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->

            val server = parent.getItemAtPosition(position) as ServerSetting
            if (manageMode) {
                editServerByIndex(position + 1)
            } else {
                setActiveServerById(server.id)
                findNavController().popBackStack(R.id.mainFragment, false)
            }
        }

        val fab = view.findViewById<FloatingActionButton>(R.id.server_add_fab)
        fab.setOnClickListener {
            editServerByIndex(-1)
        }
    }

    override fun onResume() {
        super.onResume()
        val serverList = serverSettingsModel.getServerList()
        serverList.observe(
            this
        ) { t ->
            serverRowAdapter!!.setData(t.toTypedArray())
        }
    }

    /**
     * Sets the active server when a list item is clicked
     */
    private fun setActiveServerById(id: Int) {

        controller.clearIncomplete()

        if (activeServerProvider.getActiveServer().id != id) {
            ActiveServerProvider.setActiveServerById(id)
        }
    }

    /**
     * This Callback handles the deletion of a Server Setting
     */
    private fun deleteServerById(id: Int) {
        ErrorDialog.Builder(context)
            .setTitle(R.string.server_menu_delete)
            .setMessage(R.string.server_selector_delete_confirmation)
            .setPositiveButton(R.string.common_delete) { dialog, _ ->
                dialog.dismiss()

                // Get the id of the current active server
                val activeServerId = ActiveServerProvider.getActiveServerId()

                // If the currently active server is deleted, go offline
                if (id == activeServerId) setActiveServerById(ActiveServerProvider.OFFLINE_DB_ID)

                serverSettingsModel.deleteItemById(id)

                // Clear the metadata cache
                activeServerProvider.deleteMetaDatabase(activeServerId)

                Timber.i("Server deleted, id: $id")
            }
            .setNegativeButton(R.string.common_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Starts the Edit Server Fragment to edit the details of a server
     */
    private fun editServerByIndex(index: Int) {
        val bundle = Bundle()
        bundle.putInt(EDIT_SERVER_INTENT_INDEX, index)
        findNavController().navigate(R.id.serverSelectorToEditServer, bundle)
    }
}
