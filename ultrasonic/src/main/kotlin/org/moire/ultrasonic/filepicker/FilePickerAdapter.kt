package org.moire.ultrasonic.filepicker

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Environment
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatEditText
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.LinkedList
import org.moire.ultrasonic.R
import org.moire.ultrasonic.util.Util
import timber.log.Timber

/**
 * Adapter for the RecyclerView which handles listing, navigating and picking files
 * @author this implementation is loosely based on the work of Yogesh Sundaresan,
 * original license: http://www.apache.org/licenses/LICENSE-2.0
 */
internal class FilePickerAdapter(view: FilePickerView) :
    RecyclerView.Adapter<FilePickerAdapter.FileListHolder>() {

    private var data: MutableList<FileListItem> = LinkedList()
    var defaultDirectory: File = Environment.getExternalStorageDirectory()
    var initialDirectory: File = Environment.getExternalStorageDirectory()
    lateinit var selectedDirectoryChanged: (String, Boolean) -> Unit
    var selectedDirectory: File = defaultDirectory
        private set

    private var context: Context? = null
    private var listerView: FilePickerView? = view
    private var isRealDirectory: Boolean = false

    private var folderIcon: Drawable? = null
    private var upIcon: Drawable? = null
    private var sdIcon: Drawable? = null

    init {
        this.context = view.context
        listerView = view

        upIcon = Util.getDrawableFromAttribute(context!!, R.attr.filepicker_subdirectory_up)
        folderIcon = Util.getDrawableFromAttribute(context!!, R.attr.filepicker_folder)
        sdIcon = Util.getDrawableFromAttribute(context!!, R.attr.filepicker_sd_card)
    }

    fun start() {
        fileLister(initialDirectory)
    }

    private fun fileLister(currentDirectory: File) {
        var fileList = LinkedList<FileListItem>()
        val storages: List<File>?
        val storagePaths: List<String>?
        storages = context!!.getExternalFilesDirs(null).filterNotNull()
        storagePaths = storages.map { i -> i.absolutePath }

        if (currentDirectory.absolutePath == "/" ||
            currentDirectory.absolutePath == "/storage" ||
            currentDirectory.absolutePath == "/storage/emulated" ||
            currentDirectory.absolutePath == "/mnt"
        ) {
            isRealDirectory = false
            fileList = getKitKatStorageItems(storages)
        } else {
            isRealDirectory = true
            val files = currentDirectory.listFiles()
            files?.forEach { file ->
                if (file.isDirectory) {
                    fileList.add(FileListItem(file, file.name, folderIcon!!))
                }
            }
        }

        data = LinkedList(fileList)

        data.sortWith { f1, f2 ->
            if (f1.file!!.isDirectory && f2.file!!.isDirectory)
                f1.name.compareTo(f2.name, ignoreCase = true)
            else if (f1.file!!.isDirectory && !f2.file!!.isDirectory)
                -1
            else if (!f1.file!!.isDirectory && f2.file!!.isDirectory)
                1
            else if (!f1.file!!.isDirectory && !f2.file!!.isDirectory)
                f1.name.compareTo(f2.name, ignoreCase = true)
            else
                0
        }

        selectedDirectory = currentDirectory
        selectedDirectoryChanged.invoke(
            if (isRealDirectory) selectedDirectory.absolutePath
            else context!!.getString(R.string.filepicker_available_drives),
            isRealDirectory
        )

        // Add the "Up" navigation to the list
        if (currentDirectory.absolutePath != "/" && isRealDirectory) {
            // If we are on KitKat or later, only the default App folder is usable, so we can't
            // navigate the SD card. Jump to the root if "Up" is selected.
            if (storagePaths.indexOf(currentDirectory.absolutePath) > 0)
                data.add(0, FileListItem(File("/"), "..", upIcon!!))
            else
                data.add(0, FileListItem(selectedDirectory.parentFile!!, "..", upIcon!!))
        }

        notifyDataSetChanged()
        listerView!!.scrollToPosition(0)
    }

    private fun getKitKatStorageItems(storages: List<File>): LinkedList<FileListItem> {
        val fileList = LinkedList<FileListItem>()
        if (storages.isNotEmpty()) {
            for ((index, file) in storages.withIndex()) {
                var path = file.absolutePath
                path = path.replace("/Android/data/([a-zA-Z_][.\\w]*)/files".toRegex(), "")
                if (index == 0) {
                    fileList.add(
                        FileListItem(
                            File(path),
                            context!!.getString(R.string.filepicker_internal, path),
                            sdIcon!!
                        )
                    )
                } else {
                    fileList.add(
                        FileListItem(
                            file,
                            context!!.getString(R.string.filepicker_default_app_folder, path),
                            sdIcon!!
                        )
                    )
                }
            }
        }
        return fileList
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileListHolder {
        return FileListHolder(
            LayoutInflater.from(context).inflate(
                R.layout.filepicker_item_file_lister, listerView, false
            )
        )
    }

    override fun onBindViewHolder(holder: FileListHolder, position: Int) {
        val actualFile = data[position]

        holder.name.text = actualFile.name
        holder.icon.setImageDrawable(actualFile.icon)
    }

    override fun getItemCount(): Int {
        return data.size
    }

    fun goToDefault() {
        fileLister(defaultDirectory)
    }

    fun createNewFolder() {
        val view = View.inflate(context, R.layout.filepicker_dialog_create_folder, null)
        val editText = view.findViewById<AppCompatEditText>(R.id.edittext)
        val builder = AlertDialog.Builder(context!!)
            .setView(view)
            .setTitle(context!!.getString(R.string.filepicker_enter_folder_name))
            .setPositiveButton(context!!.getString(R.string.filepicker_create)) { _, _ -> }
        val dialog = builder.create()
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = editText.text!!.toString()

            if (TextUtils.isEmpty(name)) {
                Util.toast(context!!, context!!.getString(R.string.filepicker_name_invalid))
            } else {
                val file = File(selectedDirectory, name)

                if (file.exists()) {
                    Util.toast(context!!, context!!.getString(R.string.filepicker_already_exists))
                } else {
                    dialog.dismiss()
                    if (file.mkdirs()) {
                        fileLister(file)
                    } else {
                        Util.toast(
                            context!!,
                            context!!.getString(R.string.filepicker_create_folder_failed)
                        )
                    }
                }
            }
        }
    }

    internal inner class FileListItem(
        fileParameter: File,
        nameParameter: String,
        iconParameter: Drawable
    ) {
        var file: File? = fileParameter
        var name: String = nameParameter
        var icon: Drawable? = iconParameter
    }

    internal inner class FileListHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

        var name: TextView = itemView.findViewById(R.id.name)
        var icon: ImageView = itemView.findViewById(R.id.icon)

        init {
            itemView.findViewById<View>(R.id.layout).setOnClickListener(this)
        }

        override fun onClick(v: View) {
            val clickedFile = data[adapterPosition]
            selectedDirectory = clickedFile.file!!
            fileLister(clickedFile.file!!)
            Timber.d(clickedFile.file!!.absolutePath)
        }
    }
}
