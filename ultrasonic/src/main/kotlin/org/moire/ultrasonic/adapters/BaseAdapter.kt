package org.moire.ultrasonic.adapters

import android.annotation.SuppressLint
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.AdapterListUpdateCallback
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.AsyncListDiffer.ListListener
import androidx.recyclerview.widget.DiffUtil
import com.drakeet.multitype.MultiTypeAdapter
import java.util.TreeSet
import org.moire.ultrasonic.domain.Identifiable

class BaseAdapter<T : Identifiable> : MultiTypeAdapter() {

    internal var selectedSet: TreeSet<Long> = TreeSet()
    internal var selectionRevision: MutableLiveData<Int> = MutableLiveData(0)

    private val diffCallback = GenericDiffCallback<T>()

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).longId
    }

    private fun getItem(position: Int): T {
        return mDiffer.currentList[position]
    }

    override var items: List<Any>
        get() = getCurrentList()
        set(value) {
            throw IllegalAccessException("You must use submitList() to add data to the Adapter")
        }

    var mDiffer: AsyncListDiffer<T> = AsyncListDiffer(
        AdapterListUpdateCallback(this),
        AsyncDifferConfig.Builder(diffCallback).build()
    )

    private val mListener =
        ListListener<T> { previousList, currentList ->
            this@BaseAdapter.onCurrentListChanged(
                previousList,
                currentList
            )
        }

    init {
        mDiffer.addListListener(mListener)
    }

    /**
     * Submits a new list to be diffed, and displayed.
     *
     *
     * If a list is already being displayed, a diff will be computed on a background thread, which
     * will dispatch Adapter.notifyItem events on the main thread.
     *
     * @param list The new list to be displayed.
     */
    fun submitList(list: List<T>?) {
        mDiffer.submitList(list)
    }

    /**
     * Set the new list to be displayed.
     *
     *
     * If a List is already being displayed, a diff will be computed on a background thread, which
     * will dispatch Adapter.notifyItem events on the main thread.
     *
     *
     * The commit callback can be used to know when the List is committed, but note that it
     * may not be executed. If List B is submitted immediately after List A, and is
     * committed directly, the callback associated with List A will not be run.
     *
     * @param list The new list to be displayed.
     * @param commitCallback Optional runnable that is executed when the List is committed, if
     * it is committed.
     */
    fun submitList(list: List<T>?, commitCallback: Runnable?) {
        mDiffer.submitList(list, commitCallback)
    }

    override fun getItemCount(): Int {
        return mDiffer.currentList.size
    }

    /**
     * Get the current List - any diffing to present this list has already been computed and
     * dispatched via the ListUpdateCallback.
     *
     *
     * If a `null` List, or no List has been submitted, an empty list will be returned.
     *
     *
     * The returned list may not be mutated - mutations to content must be done through
     * [.submitList].
     *
     * @return The list currently being displayed.
     *
     * @see .onCurrentListChanged
     */
    fun getCurrentList(): List<T> {
        return mDiffer.currentList
    }

    /**
     * Called when the current List is updated.
     *
     *
     * If a `null` List is passed to [.submitList], or no List has been
     * submitted, the current List is represented as an empty List.
     *
     * @param previousList List that was displayed previously.
     * @param currentList new List being displayed, will be empty if `null` was passed to
     * [.submitList].
     *
     * @see .getCurrentList
     */
    fun onCurrentListChanged(previousList: List<T>, currentList: List<T>) {
        // Void
    }

    fun notifySelected(id: Long) {
        selectedSet.add(id)

        // Update revision counter
        selectionRevision.postValue(selectionRevision.value!! + 1)
    }

    fun notifyUnselected(id: Long) {
        selectedSet.remove(id)

        // Update revision counter
        selectionRevision.postValue(selectionRevision.value!! + 1)
    }

    fun notifyChanged() {
        // When the download state of an entry was changed by an external process,
        // increase the revision counter in order to update the UI

        selectionRevision.postValue(selectionRevision.value!! + 1)
    }

    fun setSelectionStatusOfAll(select: Boolean): Int {
        // Clear current selection
        selectedSet.clear()

        // Update revision counter
        selectionRevision.postValue(selectionRevision.value!! + 1)

        // Nothing to reselect
        if (!select) return 0

        // Select them all
        getCurrentList().mapNotNullTo(
            selectedSet,
            { entry ->
                // Exclude any -1 ids, eg. headers and other UI elements
                entry.longId.takeIf { it != -1L }
            }
        )

        return selectedSet.count()
    }

    fun isSelected(longId: Long): Boolean {
        return selectedSet.contains(longId)
    }

    fun moveItem(from: Int, to: Int): List<T> {
        val list = getCurrentList().toMutableList()
        val fromLocation = list[from]
        list.removeAt(from)
        if (to < from) {
            list.add(to + 1, fromLocation)
        } else {
            list.add(to - 1, fromLocation)
        }
        submitList(list)
        return list as List<T>
    }

    companion object {
        /**
         * Calculates the differences between data sets
         */
        class GenericDiffCallback<T : Identifiable> : DiffUtil.ItemCallback<T>() {
            @SuppressLint("DiffUtilEquals")
            override fun areContentsTheSame(oldItem: T, newItem: T): Boolean {
                return oldItem == newItem
            }

            override fun areItemsTheSame(oldItem: T, newItem: T): Boolean {
                return oldItem.id == newItem.id
            }
        }
    }
}
