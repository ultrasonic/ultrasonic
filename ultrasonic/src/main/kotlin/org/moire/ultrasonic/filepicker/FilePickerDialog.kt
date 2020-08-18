package org.moire.ultrasonic.filepicker

import android.content.Context
import android.content.DialogInterface.BUTTON_NEGATIVE
import android.content.DialogInterface.BUTTON_NEUTRAL
import android.content.DialogInterface.BUTTON_POSITIVE
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.moire.ultrasonic.R

/**
 * This dialog can be used to pick a file / folder from the filesystem.
 * Currently only supports folders.
 * @author this implementation is loosely based on the work of Yogesh Sundaresan,
 * original license: http://www.apache.org/licenses/LICENSE-2.0
 */
class FilePickerDialog {

    private var alertDialog: AlertDialog? = null
    private var filePickerView: FilePickerView? = null
    private var onFileSelectedListener: OnFileSelectedListener? = null
    private var currentPath: TextView? = null
    private var newFolderButton: Button? = null

    private constructor(context: Context) {
        alertDialog = AlertDialog.Builder(context).create()
        initialize(context)
    }

    private constructor(context: Context, themeResId: Int) {
        alertDialog = AlertDialog.Builder(context, themeResId).create()
        initialize(context)
    }

    private fun initialize(context: Context) {
        val view = LayoutInflater.from(context).inflate(R.layout.filepicker_dialog_main, null)

        alertDialog!!.setView(view)
        filePickerView = view.findViewById(R.id.file_list_view)
        currentPath = view.findViewById(R.id.current_path)

        newFolderButton = view.findViewById(R.id.filepicker_create_folder)
        newFolderButton!!.setOnClickListener { filePickerView!!.createNewFolder() }

        alertDialog!!.setTitle(context.getString(R.string.filepicker_select_folder))

        alertDialog!!.setButton(BUTTON_POSITIVE, context.getString(R.string.filepicker_select)) {
            dialogInterface, _ ->
            dialogInterface.dismiss()
            if (onFileSelectedListener != null)
                onFileSelectedListener!!.onFileSelected(
                    filePickerView!!.selected, filePickerView!!.selected.absolutePath
                )
        }
        alertDialog!!.setButton(BUTTON_NEUTRAL, context.getString(R.string.filepicker_default)) {
            _, _ ->
            filePickerView!!.goToDefaultDirectory()
        }
        alertDialog!!.setButton(BUTTON_NEGATIVE, context.getString(R.string.common_cancel)) {
            dialogInterface, _ ->
            dialogInterface.dismiss()
        }
    }

    /**
     * Display the FilePickerDialog
     */
    fun show() {
        filePickerView!!.start { currentDirectory, isRealPath ->
            run {
                currentPath?.text = currentDirectory
                newFolderButton!!.isEnabled = isRealPath
            }
        }
        alertDialog!!.show()
        alertDialog!!.getButton(BUTTON_NEUTRAL).setOnClickListener {
            filePickerView!!.goToDefaultDirectory()
        }
    }

    /**
     * Listener to know which file/directory is selected
     *
     * @param onFileSelectedListener Instance of the Listener
     */
    fun setOnFileSelectedListener(onFileSelectedListener: OnFileSelectedListener) {
        this.onFileSelectedListener = onFileSelectedListener
    }

    /**
     * Set the initial directory to show the list of files in that directory
     *
     * @param path String denoting to the directory
     */
    fun setDefaultDirectory(path: String) {
        filePickerView!!.setDefaultDirectory(path)
    }

    fun setInitialDirectory(path: String) {
        filePickerView!!.setInitialDirectory(path)
    }

    companion object {
        /**
         * Creates a default instance of FilePickerDialog
         *
         * @param context Context of the App
         * @return Instance of FileListerDialog
         */
        fun createFilePickerDialog(context: Context): FilePickerDialog {
            return FilePickerDialog(context)
        }
    }
}
