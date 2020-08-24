package org.moire.ultrasonic.filepicker

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

/**
 * RecyclerView containing the file list of a directory
 * @author this implementation is loosely based on the work of Yogesh Sundaresan,
 * original license: http://www.apache.org/licenses/LICENSE-2.0
 */
internal class FilePickerView : RecyclerView {

    private var adapter: FilePickerAdapter? = null

    val selected: File
        get() = adapter!!.selectedDirectory

    constructor(context: Context) : super(context) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initialize()
    }

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyle: Int
    ) : super(context, attrs, defStyle) {
        initialize()
    }

    private fun initialize() {
        layoutManager = LinearLayoutManager(context, VERTICAL, false)
        adapter = FilePickerAdapter(this)
    }

    fun start(selectedDirectoryChangedListener: (String, Boolean) -> Unit) {
        setAdapter(adapter)
        adapter?.selectedDirectoryChanged = selectedDirectoryChangedListener
        adapter!!.start()
    }

    fun setDefaultDirectory(file: File) {
        adapter!!.defaultDirectory = file
    }

    fun setDefaultDirectory(path: String) {
        setDefaultDirectory(File(path))
    }

    fun setInitialDirectory(path: String) {
        adapter!!.initialDirectory = File(path)
    }

    fun goToDefaultDirectory() {
        adapter!!.goToDefault()
    }

    fun createNewFolder() {
        adapter!!.createNewFolder()
    }
}
