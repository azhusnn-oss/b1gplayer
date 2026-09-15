package com.b1g.player.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ContentDao {

    // Inserts are deliberately blocking: they run inside one transaction driven by
    // the playlist parse loop, which is already on an IO thread.

    @Insert
    fun insertChannels(rows: List<ChannelEntity>)

    @Insert
    fun insertVod(rows: List<VodEntity>)

    @Insert
    fun insertEpisodes(rows: List<EpisodeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertMeta(meta: SourceMetaEntity)

    @Query("DELETE FROM channels WHERE sourceId = :sourceId")
    fun deleteChannels(sourceId: String)

    @Query("DELETE FROM vod WHERE sourceId = :sourceId")
    fun deleteVod(sourceId: String)

    @Query("DELETE FROM episodes WHERE sourceId = :sourceId")
    fun deleteEpisodes(sourceId: String)

    @Query("DELETE FROM source_meta WHERE sourceId = :sourceId")
    fun deleteMeta(sourceId: String)

    @Query(
        """
        SELECT * FROM channels
        WHERE sourceId = :sourceId
          AND (:categoryId IS NULL OR categoryId = :categoryId)
          AND (:query IS NULL OR name LIKE :query ESCAPE '\')
        ORDER BY rowId
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun channels(
        sourceId: String,
        categoryId: String?,
        query: String?,
        limit: Int,
        offset: Int,
    ): List<ChannelEntity>

    @Query(
        """
        SELECT * FROM vod
        WHERE sourceId = :sourceId
          AND (:categoryId IS NULL OR categoryId = :categoryId)
          AND (:query IS NULL OR name LIKE :query ESCAPE '\')
        ORDER BY rowId
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun vod(
        sourceId: String,
        categoryId: String?,
        query: String?,
        limit: Int,
        offset: Int,
    ): List<VodEntity>

    @Query(
        """
        SELECT categoryId AS categoryId, MIN(categoryName) AS categoryName FROM channels
        WHERE sourceId = :sourceId AND categoryId IS NOT NULL
        GROUP BY categoryId
        ORDER BY categoryName
        """
    )
    suspend fun channelCategories(sourceId: String): List<CategoryRow>

    @Query(
        """
        SELECT categoryId AS categoryId, MIN(categoryName) AS categoryName FROM vod
        WHERE sourceId = :sourceId AND categoryId IS NOT NULL
        GROUP BY categoryId
        ORDER BY categoryName
        """
    )
    suspend fun vodCategories(sourceId: String): List<CategoryRow>

    @Query(
        """
        SELECT seriesId AS seriesId,
               MIN(seriesName) AS seriesName,
               MIN(seriesCover) AS seriesCover,
               MIN(categoryId) AS categoryId,
               MIN(categoryName) AS categoryName,
               COUNT(*) AS episodeCount
        FROM episodes
        WHERE sourceId = :sourceId
          AND (:categoryId IS NULL OR categoryId = :categoryId)
          AND (:query IS NULL OR seriesName LIKE :query ESCAPE '\')
        GROUP BY seriesId
        ORDER BY seriesName
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun series(
        sourceId: String,
        categoryId: String?,
        query: String?,
        limit: Int,
        offset: Int,
    ): List<SeriesRow>

    @Query(
        """
        SELECT * FROM episodes
        WHERE sourceId = :sourceId AND seriesId = :seriesId
        ORDER BY seasonNumber, episodeNumber
        """
    )
    suspend fun episodes(sourceId: String, seriesId: String): List<EpisodeEntity>

    @Query(
        """
        SELECT categoryId AS categoryId, MIN(categoryName) AS categoryName FROM episodes
        WHERE sourceId = :sourceId AND categoryId IS NOT NULL
        GROUP BY categoryId
        ORDER BY categoryName
        """
    )
    suspend fun seriesCategories(sourceId: String): List<CategoryRow>

    @Query("SELECT COUNT(*) FROM episodes WHERE sourceId = :sourceId")
    suspend fun episodeCount(sourceId: String): Int

    @Query("SELECT COUNT(*) FROM channels WHERE sourceId = :sourceId")
    suspend fun channelCount(sourceId: String): Int

    @Query("SELECT COUNT(*) FROM vod WHERE sourceId = :sourceId")
    suspend fun vodCount(sourceId: String): Int

    @Query("SELECT * FROM source_meta WHERE sourceId = :sourceId")
    suspend fun meta(sourceId: String): SourceMetaEntity?
}
