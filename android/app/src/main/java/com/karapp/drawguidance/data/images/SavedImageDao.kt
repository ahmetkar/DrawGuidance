package com.karapp.drawguidance.data.images

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedImageDao {
    @Query("SELECT * FROM saved_images ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SavedImageEntity>>

    @Query("SELECT * FROM saved_images WHERE id = :id")
    suspend fun getById(id: Long): SavedImageEntity?

    @Insert
    suspend fun insert(entity: SavedImageEntity): Long

    @Query("UPDATE saved_images SET sizeBytes = :sizeBytes WHERE id = :id")
    suspend fun updateSize(id: Long, sizeBytes: Long)

    @Query("DELETE FROM saved_images WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM saved_images WHERE folderId IN (:folderIds)")
    suspend fun inFolders(folderIds: List<String>): List<SavedImageEntity>

    @Query("UPDATE saved_images SET folderId = :folderId WHERE id IN (:ids)")
    suspend fun moveTo(ids: List<Long>, folderId: String?)

    /** v17: resim kayıt yerinde başka klasöre taşındı. */
    @Query("UPDATE saved_images SET uri = :uri, location = :location WHERE id = :id")
    suspend fun updateLocation(id: Long, uri: String, location: String)
}
