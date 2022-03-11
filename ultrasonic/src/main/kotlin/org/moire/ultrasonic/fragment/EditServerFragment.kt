package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputLayout
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.flag.BubbleFlag
import com.skydoves.colorpickerview.flag.FlagMode
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import java.util.Locale
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.moire.ultrasonic.BuildConfig
import org.moire.ultrasonic.R
import org.moire.ultrasonic.api.subsonic.SubsonicAPIClient
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicClientConfiguration
import org.moire.ultrasonic.api.subsonic.SubsonicRESTException
import org.moire.ultrasonic.api.subsonic.falseOnFailure
import org.moire.ultrasonic.api.subsonic.response.SubsonicResponse
import org.moire.ultrasonic.api.subsonic.throwOnFailure
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.ServerSetting
import org.moire.ultrasonic.model.ServerSettingsModel
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.ErrorDialog
import org.moire.ultrasonic.util.InfoDialog
import org.moire.ultrasonic.util.ModalBackgroundTask
import org.moire.ultrasonic.util.ServerColor
import org.moire.ultrasonic.util.Util
import retrofit2.Response
import timber.log.Timber

private const val DIALOG_PADDING = 12

/**
 * Displays a form where server settings can be created / edited
 */
class EditServerFragment : Fragment(), OnBackPressedHandler {
    companion object {
        const val EDIT_SERVER_INTENT_INDEX = "index"
    }

    private val serverSettingsModel: ServerSettingsModel by viewModel()
    private val activeServerProvider: ActiveServerProvider by inject()

    private var currentServerSetting: ServerSetting? = null

    private var serverNameEditText: TextInputLayout? = null
    private var serverAddressEditText: TextInputLayout? = null
    private var serverColorImageView: ImageView? = null
    private var userNameEditText: TextInputLayout? = null
    private var passwordEditText: TextInputLayout? = null
    private var selfSignedSwitch: SwitchMaterial? = null
    private var ldapSwitch: SwitchMaterial? = null
    private var jukeboxSwitch: SwitchMaterial? = null
    private var saveButton: Button? = null
    private var testButton: Button? = null
    private var isInstanceStateSaved: Boolean = false
    private var currentColor: Int = 0
    private var selectedColor: Int? = null

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
        return inflater.inflate(R.layout.server_edit, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        serverNameEditText = view.findViewById(R.id.edit_server_name)
        serverAddressEditText = view.findViewById(R.id.edit_server_address)
        serverColorImageView = view.findViewById(R.id.edit_server_color_picker)
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
                viewLifecycleOwner
            ) { t ->
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
            updateColor(null)
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

        serverColorImageView!!.setOnClickListener {
            val bubbleFlag = BubbleFlag(context)
            bubbleFlag.flagMode = FlagMode.LAST
            ColorPickerDialog.Builder(context).apply {
                this.colorPickerView.setInitialColor(currentColor)
                this.colorPickerView.flagView = bubbleFlag
            }
                .attachAlphaSlideBar(false)
                .setPositiveButton(
                    getString(R.string.common_ok),
                    ColorEnvelopeListener { envelope, _ ->
                        selectedColor = envelope.color
                        updateColor(envelope.color)
                    }
                )
                .setNegativeButton(getString(R.string.common_cancel)) {
                    dialogInterface, _ ->
                    dialogInterface.dismiss()
                }
                .setBottomSpace(DIALOG_PADDING)
                .show()
        }
    }

    override fun onStop() {
        Util.hideKeyboard(activity)
        super.onStop()
    }

    private fun updateColor(color: Int?) {
        val image = ContextCompat.getDrawable(requireContext(), R.drawable.thumb_drawable)
        currentColor = ServerColor.getBackgroundColor(requireContext(), color)
        image?.setTint(currentColor)
        serverColorImageView?.background = image
    }

    override fun onBackPressed() {
        finishActivity()
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
        savedInstanceState.putInt(
            ::serverColorImageView.name, currentColor
        )
        if (selectedColor != null)
            savedInstanceState.putInt(
                ::selectedColor.name, selectedColor!!
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
        updateColor(savedInstanceState.getInt(::serverColorImageView.name))
        if (savedInstanceState.containsKey(::selectedColor.name))
            selectedColor = savedInstanceState.getInt(::selectedColor.name)
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
        updateColor(currentServerSetting!!.color)
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
            currentServerSetting!!.color = selectedColor
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
            return serverNameEditText!!.editText?.text!!.isNotBlank() ||
                serverAddressEditText!!.editText?.text.toString() != "http://" ||
                userNameEditText!!.editText?.text!!.isNotBlank() ||
                passwordEditText!!.editText?.text!!.isNotBlank()
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
        val task: ModalBackgroundTask<String> = object : ModalBackgroundTask<String>(
            activity,
            false
        ) {
            fun boolToMark(value: Boolean?): String {
                if (value == null)
                    return "⌛"
                return if (value) "✔️" else "❌"
            }

            fun getProgress(): String {
                return String.format(
                    """
                    |%s - ${resources.getString(R.string.button_bar_chat)}
                    |%s - ${resources.getString(R.string.button_bar_bookmarks)}
                    |%s - ${resources.getString(R.string.button_bar_shares)}
                    |%s - ${resources.getString(R.string.button_bar_podcasts)}
                    """.trimMargin(),
                    boolToMark(currentServerSetting!!.chatSupport),
                    boolToMark(currentServerSetting!!.bookmarkSupport),
                    boolToMark(currentServerSetting!!.shareSupport),
                    boolToMark(currentServerSetting!!.podcastSupport)
                )
            }

            @Throws(Throwable::class)
            override fun doInBackground(): String {

                currentServerSetting!!.chatSupport = null
                currentServerSetting!!.bookmarkSupport = null
                currentServerSetting!!.shareSupport = null
                currentServerSetting!!.podcastSupport = null

                updateProgress(getProgress())

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
                if (pingResponse.body() != null) {
                    val restApiVersion = pingResponse.body()!!.version.restApiVersion
                    currentServerSetting!!.minimumApiVersion = restApiVersion
                    Timber.i("Server minimum API version set to %s", restApiVersion)
                }

                // Execute a ping to check the authentication, now using the correct API version.
                pingResponse = subsonicApiClient.api.ping().execute()
                pingResponse.throwOnFailure()

                currentServerSetting!!.chatSupport = isServerFunctionAvailable {
                    subsonicApiClient.api.getChatMessages().execute()
                }

                updateProgress(getProgress())

                currentServerSetting!!.bookmarkSupport = isServerFunctionAvailable {
                    subsonicApiClient.api.getBookmarks().execute()
                }

                updateProgress(getProgress())

                currentServerSetting!!.shareSupport = isServerFunctionAvailable {
                    subsonicApiClient.api.getShares().execute()
                }

                updateProgress(getProgress())

                currentServerSetting!!.podcastSupport = isServerFunctionAvailable {
                    subsonicApiClient.api.getPodcasts().execute()
                }

                updateProgress(getProgress())

                val licenseResponse = subsonicApiClient.api.getLicense().execute()
                licenseResponse.throwOnFailure()

                if (!licenseResponse.body()!!.license.valid) {
                    return getProgress() + "\n" +
                        resources.getString(R.string.settings_testing_unlicensed)
                }
                return getProgress()
            }

            override fun done(responseString: String) {
                var dialogText = responseString
                if (arrayOf(
                        currentServerSetting!!.chatSupport,
                        currentServerSetting!!.bookmarkSupport,
                        currentServerSetting!!.shareSupport,
                        currentServerSetting!!.podcastSupport
                    ).any { x -> x == false }
                ) {
                    dialogText = String.format(
                        Locale.ROOT,
                        "%s\n\n%s",
                        responseString,
                        resources.getString(R.string.server_editor_disabled_feature)
                    )
                }

                InfoDialog.Builder(requireActivity())
                    .setTitle(R.string.settings_testing_ok)
                    .setMessage(dialogText)
                    .show()
            }

            override fun error(error: Throwable) {
                Timber.w(error)
                ErrorDialog(
                    context = activity,
                    message = String.format(
                        "%s %s",
                        resources.getString(R.string.settings_connection_failure),
                        getErrorMessage(error)
                    )
                ).show()
            }
        }
        task.execute()
    }

    private fun isServerFunctionAvailable(function: () -> Response<out SubsonicResponse>): Boolean {
        return try {
            function().falseOnFailure()
        } catch (_: IOException) {
            false
        } catch (_: SubsonicRESTException) {
            false
        }
    }

    /**
     * Finishes the Activity, after confirmation from the user if needed
     */
    private fun finishActivity() {
        if (areFieldsChanged()) {
            ErrorDialog.Builder(context)
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
