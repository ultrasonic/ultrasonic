package org.moire.ultrasonic.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import org.moire.ultrasonic.domain.Index
import org.moire.ultrasonic.domain.MusicFolder

@Dao
interface MusicFoldersDao : GenericDao<MusicFolder> {
    /**
     * Clear the whole database
     */
    @Query("DELETE FROM music_folders")
    fun clear()

    /**
     * Get all folders
     */
    @Query("SELECT * FROM music_folders")
    fun get(): List<MusicFolder>
}

@Dao
interface IndexDao : GenericDao<Index> {

    /**
     * Clear the whole database
     */
    @Query("DELETE FROM indexes")
    fun clear()

    /**
     * Get all indexes
     */
    @Query("SELECT * FROM indexes")
    fun get(): List<Index>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(vararg indexes: Index)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertArray(arr: Array<Index>)

    /**
     * Get all indexes for a specific folder id
     */
    @Query("SELECT * FROM indexes where musicFolderId LIKE :musicFolderId")
    fun get(musicFolderId: String): List<Index>

    /**
     * TODO: Make generic
     * Upserts (insert or update) an object to the database
     *
     * @param obj the object to upsert
     */
    @Transaction
    @JvmSuppressWildcards
    fun upsert(obj: Index) {
        val id = insertIgnoring(obj)
        if (id == -1L) {
            update(obj)
        }
    }

    /**
     * Upserts (insert or update) a list of objects
     *
     * @param objList the object to be upserted
     */
    @Transaction
    @JvmSuppressWildcards
    fun upsert(objList: List<Index>) {
        val insertResult = insertIgnoring(objList)
        val updateList: MutableList<Index> = ArrayList()
        for (i in insertResult.indices) {
            if (insertResult[i] == -1L) {
                updateList.add(objList[i])
            }
        }
        if (updateList.isNotEmpty()) {
            update(updateList)
        }
    }
}

interface GenericDao<T> {
    /**
     * Replaces the list with a new collection
     *
     * @param objects the items to be inserted.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @JvmSuppressWildcards
    fun set(objects: List<T>)

    /**
     * Insert an object in the database.
     *
     * @param obj the object to be inserted.
     * @return The SQLite row id
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @JvmSuppressWildcards
    fun insert(obj: T): Long

    /**
     * Insert an object in the database.
     *
     * @param obj the object to be inserted.
     * @return The SQLite row id
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    @JvmSuppressWildcards
    fun insertIgnoring(obj: T): Long

    /**
     * Insert an array of objects in the database.
     *
     * @param obj the objects to be inserted.
     * @return The SQLite row ids
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    @JvmSuppressWildcards
    fun insertIgnoring(obj: List<T>?): List<Long>

    /**
     * Update an object from the database.
     *
     * @param obj the object to be updated
     */
    @Update
    @JvmSuppressWildcards
    fun update(obj: T)

    /**
     * Update an array of objects from the database.
     *
     * @param obj the object to be updated
     */
    @Update
    @JvmSuppressWildcards
    fun update(obj: List<T>?)

    /**
     * Delete an object from the database
     *
     * @param obj the object to be deleted
     */
    @Delete
    @JvmSuppressWildcards
    fun delete(obj: T)
}
