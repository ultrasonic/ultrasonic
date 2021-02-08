package org.moire.ultrasonic.fragment

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputLayout
import org.koin.android.ext.android.inject
import org.koin.android.viewmodel.ext.android.viewModel
import org.moire.ultrasonic.BuildConfig
import org.moire.ultrasonic.R
import org.moire.ultrasonic.api.subsonic.SubsonicAPIClient
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicClientConfiguration
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.ServerSetting
import org.moire.ultrasonic.service.ApiCallResponseChecker
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.ErrorDialog
import org.moire.ultrasonic.util.ModalBackgroundTask
import org.moire.ultrasonic.util.Util
import timber.log.Timber
import java.net.MalformedURLException
import java.net.URL

class EditServerFragment: Fragment() {
    companion object {
        const val EDIT_SERVER_INTENT_INDEX = "index"
    }

    private val serverSettingsModel: ServerSettingsModel by viewModel()
    private val activeServerProvider: ActiveServerProvider by inject()

    private var currentServerSetting: ServerSetting? = null

    private var serverNameEditText: TextInputLayout? = null
    private var serverAddressEditText: TextInputLayout? = null
    private var userNameEditText: TextInputLayout? = null
    private var passwordEditText: TextInputLayout? = null
    private var selfSignedSwitch: SwitchMaterial? = null
    private var ldapSwitch: SwitchMaterial? = null
    private var jukeboxSwitch: SwitchMaterial? = null
    private var saveButton: Button? = null
    private var testButton: Button? = null
    private var isInstanceStateSaved: Boolean = false

    @Override
    override fun onCreate(savedInstanceState: Bundle?) {
        Util.applyTheme(this.context)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.server_edit, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        serverNameEditText = view.findViewById(R.id.edit_server_name)
        serverAddressEditText = view.findViewById(R.id.edit_server_address)
        userNameEditText = view.findViewById(R.id.edit_server_username)
        passwordEditText = view.findViewById(R.id.edit_server_password)
        selfSignedSwitch = view.findViewById(R.id.edit_self_signed)
        ldapSwitch = view.findViewById(R.id.edit_ldap)
        jukeboxSwitch = view.findViewById(R.id.edit_jukebox)
        saveButton = view.findViewById(R.id.edit_save)
        testButton = view.findViewById(R.id.edit_test)

        val index = arguments?.getInt(
            EDIT_SERVER_INTENT_INDEX,
            -1
        ) ?: -1

        if (index != -1) {
            // Editing an existing server
            FragmentTitle.setTitle(this, R.string.server_editor_label)
            val serverSetting = serverSettingsModel.getServerSetting(index)
            serverSetting.observe(
                viewLifecycleOwner,
                Observer { t ->
                    if (t != null) {
                        currentServerSetting = t
                        if (!isInstanceStateSaved) setFields()
                        // Remove the minimum API version so it can be detected again
                        if (currentServerSetting?.minimumApiVersion != null) {
                            currentServerSetting!!.minimumApiVersion = null
                            serverSettingsModel.updateItem(currentServerSetting)
                            if (
                                activeServerProvider.getActiveServer().id ==
                                currentServerSetting!!.id
                            ) {
                                MusicServiceFactory.resetMusicService()
                            }
                        }
                    }
                }
            )
            saveButton!!.setOnClickListener {
                if (currentServerSetting != null) {
                    if (getFields()) {
                        serverSettingsModel.updateItem(currentServerSetting)
                        // Apply modifications if the current server was modified
                        if (
                            activeServerProvider.getActiveServer().id ==
                            currentServerSetting!!.id
                        ) {
                            MusicServiceFactory.resetMusicService()
                        }
                        findNavController().navigateUp()
                    }
                }
            }
        } else {
            // Creating a new server
            FragmentTitle.setTitle(this, R.string.server_editor_new_label)
            currentServerSetting = ServerSetting()
            saveButton!!.setOnClickListener {
                if (getFields()) {
                    serverSettingsModel.saveNewItem(currentServerSetting)
                    findNavController().navigateUp()
                }
            }
        }

        testButton!!.setOnClickListener {
            if (getFields()) {
                testConnection()
            }
        }
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        savedInstanceState.putString(
            ::serverNameEditText.name, serverNameEditText!!.editText?.text.toString()
        )
        savedInstanceState.putString(
            ::serverAddressEditText.name, serverAddressEditText!!.editText?.text.toString()
        )
        savedInstanceState.putString(
            ::userNameEditText.name, userNameEditText!!.editText?.text.toString()
        )
        savedInstanceState.putString(
            ::passwordEditText.name, passwordEditText!!.editText?.text.toString()
        )
        savedInstanceState.putBoolean(
            ::selfSignedSwitch.name, selfSignedSwitch!!.isChecked
        )
        savedInstanceState.putBoolean(
            ::ldapSwitch.name, ldapSwitch!!.isChecked
        )
        savedInstanceState.putBoolean(
            ::jukeboxSwitch.name, jukeboxSwitch!!.isChecked
        )
        savedInstanceState.putBoolean(
            ::isInstanceStateSaved.name, true
        )

        super.onSaveInstanceState(savedInstanceState)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        if (savedInstanceState == null) return

        serverNameEditText!!.editText?.setText(
            savedInstanceState.getString(::serverNameEditText.name)
        )
        serverAddressEditText!!.editText?.setText(
            savedInstanceState.getString(::serverAddressEditText.name)
        )
        userNameEditText!!.editText?.setText(
            savedInstanceState.getString(::userNameEditText.name)
        )
        passwordEditText!!.editText?.setText(
            savedInstanceState.getString(::passwordEditText.name)
        )
        selfSignedSwitch!!.isChecked = savedInstanceState.getBoolean(::selfSignedSwitch.name)
        ldapSwitch!!.isChecked = savedInstanceState.getBoolean(::ldapSwitch.name)
        jukeboxSwitch!!.isChecked = savedInstanceState.getBoolean(::jukeboxSwitch.name)
        isInstanceStateSaved = savedInstanceState.getBoolean(::isInstanceStateSaved.name)
    }

    /**
     * Sets the values of the Form from the current Server Setting instance
     */
    private fun setFields() {
        if (currentServerSetting == null) return

        serverNameEditText!!.editText?.setText(currentServerSetting!!.name)
        serverAddressEditText!!.editText?.setText(currentServerSetting!!.url)
        userNameEditText!!.editText?.setText(currentServerSetting!!.userName)
        passwordEditText!!.editText?.setText(currentServerSetting!!.password)
        selfSignedSwitch!!.isChecked = currentServerSetting!!.allowSelfSignedCertificate
        ldapSwitch!!.isChecked = currentServerSetting!!.ldapSupport
        jukeboxSwitch!!.isChecked = currentServerSetting!!.jukeboxByDefault
    }

    /**
     * Retrieves the values in the Form to the current Server Setting instance
     * This function also does some basic validation on the fields
     */
    private fun getFields(): Boolean {
        if (currentServerSetting == null) return false
        var isValid = true
        var url: URL? = null

        if (serverAddressEditText!!.editText?.text.isNullOrBlank()) {
            serverAddressEditText!!.error = getString(R.string.server_editor_required)
            isValid = false
        } else {
            try {
                val urlString = serverAddressEditText!!.editText?.text.toString()
                url = URL(urlString)
                if (
                    urlString != urlString.trim(' ') ||
                    urlString.contains("@") ||
                    url.host.isNullOrBlank()
                ) {
                    throw MalformedURLException()
                }
                serverAddressEditText!!.error = null
            } catch (exception: MalformedURLException) {
                serverAddressEditText!!.error = getString(R.string.settings_invalid_url)
                isValid = false
            }
        }

        if (serverNameEditText!!.editText?.text.isNullOrBlank()) {
            if (isValid && url != null) {
                serverNameEditText!!.editText?.setText(url.host)
            }
        }

        if (userNameEditText!!.editText?.text.isNullOrBlank()) {
            userNameEditText!!.error = getString(R.string.server_editor_required)
            isValid = false
        } else {
            userNameEditText!!.error = null
        }

        if (isValid) {
            currentServerSetting!!.name = serverNameEditText!!.editText?.text.toString()
            currentServerSetting!!.url = serverAddressEditText!!.editText?.text.toString()
            currentServerSetting!!.userName = userNameEditText!!.editText?.text.toString()
            currentServerSetting!!.password = passwordEditText!!.editText?.text.toString()
            currentServerSetting!!.allowSelfSignedCertificate = selfSignedSwitch!!.isChecked
            currentServerSetting!!.ldapSupport = ldapSwitch!!.isChecked
            currentServerSetting!!.jukeboxByDefault = jukeboxSwitch!!.isChecked
        }

        return isValid
    }

    /**
     * Checks whether any value in the fields are changed according to their original values.
     */
    private fun areFieldsChanged(): Boolean {
        if (currentServerSetting == null || currentServerSetting!!.id == -1) {
            return !serverNameEditText!!.editText?.text!!.isBlank() ||
                serverAddressEditText!!.editText?.text.toString() != "http://" ||
                !userNameEditText!!.editText?.text!!.isBlank() ||
                !passwordEditText!!.editText?.text!!.isBlank()
        }

        return currentServerSetting!!.name != serverNameEditText!!.editText?.text.toString() ||
            currentServerSetting!!.url != serverAddressEditText!!.editText?.text.toString() ||
            currentServerSetting!!.userName != userNameEditText!!.editText?.text.toString() ||
            currentServerSetting!!.password != passwordEditText!!.editText?.text.toString() ||
            currentServerSetting!!.allowSelfSignedCertificate != selfSignedSwitch!!.isChecked ||
            currentServerSetting!!.ldapSupport != ldapSwitch!!.isChecked ||
            currentServerSetting!!.jukeboxByDefault != jukeboxSwitch!!.isChecked
    }

    /**
     * Tests if the network connection to the entered Server Settings can be made
     */
    private fun testConnection() {
        val task: ModalBackgroundTask<Boolean> = object : ModalBackgroundTask<Boolean>(
            activity,
            false
        ) {

            @Throws(Throwable::class)
            override fun doInBackground(): Boolean {
                updateProgress(R.string.settings_testing_connection)
                val configuration = SubsonicClientConfiguration(
                    currentServerSetting!!.url,
                    currentServerSetting!!.userName,
                    currentServerSetting!!.password,
                    SubsonicAPIVersions.getClosestKnownClientApiVersion(
                        Constants.REST_PROTOCOL_VERSION
                    ),
                    Constants.REST_CLIENT_ID,
                    currentServerSetting!!.allowSelfSignedCertificate,
                    currentServerSetting!!.ldapSupport,
                    BuildConfig.DEBUG
                )
                val subsonicApiClient = SubsonicAPIClient(configuration)

                // Execute a ping to retrieve the API version.
                // This is accepted to fail if the authentication is incorrect yet.
                var pingResponse = subsonicApiClient.api.ping().execute()
                if (pingResponse?.body() != null) {
                    val restApiVersion = pingResponse.body()!!.version.restApiVersion
                    currentServerSetting!!.minimumApiVersion = restApiVersion
                    Timber.i("Server minimum API version set to %s", restApiVersion)
                }

                // Execute a ping to check the authentication, now using the correct API version.
                pingResponse = subsonicApiClient.api.ping().execute()
                ApiCallResponseChecker.checkResponseSuccessful(pingResponse)

                val licenseResponse = subsonicApiClient.api.getLicense().execute()
                ApiCallResponseChecker.checkResponseSuccessful(licenseResponse)
                return licenseResponse.body()!!.license.valid
            }

            override fun done(licenseValid: Boolean) {
                if (licenseValid) {
                    Util.toast(activity, R.string.settings_testing_ok)
                } else {
                    Util.toast(activity, R.string.settings_testing_unlicensed)
                }
            }

            override fun error(error: Throwable) {
                Timber.w(error)
                ErrorDialog(
                    activity,
                    String.format(
                        "%s %s",
                        resources.getString(R.string.settings_connection_failure),
                        getErrorMessage(error)
                    ),
                    false
                )
            }
        }
        task.execute()
    }

    /**
     * Finishes the Activity, after confirmation from the user if needed
     */
    private fun finishActivity() {
        if (areFieldsChanged()) {
            AlertDialog.Builder(context)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(R.string.common_confirm)
                .setMessage(R.string.server_editor_leave_confirmation)
                .setPositiveButton(R.string.common_ok) { dialog, _ ->
                    dialog.dismiss()
                    findNavController().navigateUp()
                }
                .setNegativeButton(R.string.common_cancel) { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        } else {
            findNavController().navigateUp()
        }
    }
}