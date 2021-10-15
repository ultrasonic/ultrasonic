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
import kotlin.Comparator
import org.moire.ultrasonic.R
import org.moire.ultrasonic.util.Util
import timber.log.Timber

/**
 * Adapter for the RecyclerView which handles listing, navigating and picking files
 * @author this implementation is loosely based on the work of Yogesh Sundaresan,
 * original license: http://www.apache.org/licenses/LICENSE-2.0
 */
internal class FilePickerAdapter : RecyclerView.Adapter<FilePickerAdapter.FileListHolder> {

    private var data: MutableList<FileListItem> = LinkedList()
    var defaultDirectory: File = Environment.getExternalStorageDirectory()
    var initialDirectory: File = Environment.getExternalStorageDirectory()
    lateinit var selectedDirectoryChanged: (String, Boolean) -> Unit
    var selectedDirectory: File = defaultDirectory
        private set

    private var context: Context? = null
    private var listerView: FilePickerView? = null
    private var isRealDirectory: Boolean = false

    private val physicalPaths: Array<String>
        get() = arrayOf(
            "/storage/sdcard0", "/storage/sdcard1", "/storage/extsdcard",
            "/storage/sdcard0/external_sdcard", "/mnt/extsdcard", "/mnt/sdcard/external_sd",
            "/mnt/external_sd", "/mnt/media_rw/sdcard1", "/removable/microsd", "/mnt/emmc",
            "/storage/external_SD", "/storage/ext_sd", "/storage/removable/sdcard1",
            "/data/sdext", "/data/sdext2", "/data/sdext3", "/data/sdext4", "/sdcard1",
            "/sdcard2", "/storage/microsd", "/data/user"
        )

    private var folderIcon: Drawable? = null
    private var upIcon: Drawable? = null
    private var sdIcon: Drawable? = null

    constructor(defaultDir: File, view: FilePickerView) : this(view) {
        this.defaultDirectory = defaultDir
        selectedDirectory = defaultDir
    }

    constructor(view: FilePickerView) {
        this.context = view.context
        listerView = view

        upIcon = Util.getDrawableFromAttribute(context, R.attr.filepicker_subdirectory_up)
        folderIcon = Util.getDrawableFromAttribute(context, R.attr.filepicker_folder)
        sdIcon = Util.getDrawableFromAttribute(context, R.attr.filepicker_sd_card)
    }

    fun start() {
        fileLister(initialDirectory)
    }

    private fun fileLister(currentDirectory: File) {
        var fileList = LinkedList<FileListItem>()
        var storages: List<File>? = null
        var storagePaths: List<String>? = null
        storages = context!!.getExternalFilesDirs(null).filterNotNull()
        storagePaths = storages.map { i -> i.absolutePath }

        if (currentDirectory.absolutePath == "/" ||
            currentDirectory.absolutePath == "/storage" ||
            currentDirectory.absolutePath == "/storage/emulated" ||
            currentDirectory.absolutePath == "/mnt"
        ) {
            isRealDirectory = false
            fileList =
                getKitKatStorageItems(storages!!)
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

        data.sortWith(
            Comparator { f1, f2 ->
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
        )

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
            if (storagePaths!!.indexOf(currentDirectory.absolutePath) > 0)
                data.add(0, FileListItem(File("/"), "..", upIcon!!))
            else
                data.add(0, FileListItem(selectedDirectory.parentFile!!, "..", upIcon!!))
        }

        notifyDataSetChanged()
        listerView!!.scrollToPosition(0)
    }

    private fun getStorageItems(): LinkedList<FileListItem> {
        val fileList = LinkedList<FileListItem>()
        var s = System.getenv("EXTERNAL_STORAGE")
        if (!TextUtils.isEmpty(s)) {
            val f = File(s!!)
            fileList.add(FileListItem(f, f.name, sdIcon!!))
        } else {
            val paths = physicalPaths
            for (path in paths) {
                val f = File(path)
                if (f.exists())
                    fileList.add(FileListItem(f, f.name, sdIcon!!))
            }
        }
        s = System.getenv("SECONDARY_STORAGE")
        if (s != null && !TextUtils.isEmpty(s)) {
            val rawSecondaryStorages =
                s.split(File.pathSeparator.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (path in rawSecondaryStorages) {
                val f = File(path)
                if (f.exists())
                    fileList.add(FileListItem(f, f.name, sdIcon!!))
            }
        }
        return fileList
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
