package org.moire.ultrasonic.adapters

import android.annotation.SuppressLint
import android.view.MotionEvent
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.selection.ItemKeyProvider
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.widget.AdapterListUpdateCallback
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.AsyncListDiffer.ListListener
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.drakeet.multitype.MultiTypeAdapter
import org.moire.ultrasonic.domain.Identifiable
import timber.log.Timber

class MultiTypeDiffAdapter<T : Identifiable> : MultiTypeAdapter() {

    val diffCallback = GenericDiffCallback<T>()
    var tracker: SelectionTracker<Long>? = null

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).longId
    }

    override var items: List<Any>
        get() = getCurrentList()
        set(value) {
            throw Exception("You must use submitList() to add data to the MultiTypeDiffAdapter")
        }


    var mDiffer: AsyncListDiffer<T> = AsyncListDiffer(
        AdapterListUpdateCallback(this),
        AsyncDifferConfig.Builder(diffCallback).build()
    )

    private val mListener =
        ListListener<T> { previousList, currentList ->
            this@MultiTypeDiffAdapter.onCurrentListChanged(
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

    protected fun getItem(position: Int): T {
        return mDiffer.currentList[position]
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
