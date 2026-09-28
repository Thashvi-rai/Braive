package com.example.brailleglove.data.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.brailleglove.data.model.Level

@Dao
interface LevelDao {

    @Query("SELECT * FROM levels ORDER BY levelNumber ASC")
    fun getAllLevels(): LiveData<List<Level>>

    @Query("SELECT * FROM levels WHERE id = :levelId LIMIT 1")
    suspend fun getLevelById(levelId: Int): Level?

    @Query("SELECT * FROM levels WHERE isUnlocked = 1 ORDER BY levelNumber ASC")
    fun getUnlockedLevels(): LiveData<List<Level>>

    /** One-shot fetch of completed levels — used to pull "review" letters into later sessions. */
    @Query("SELECT * FROM levels WHERE isCompleted = 1 ORDER BY levelNumber ASC")
    suspend fun getCompletedLevelsOnce(): List<Level>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLevel(level: Level)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllLevels(levels: List<Level>)

    @Update
    suspend fun updateLevel(level: Level)

    @Query("UPDATE levels SET isCompleted = 1, bestScore = :score, stars = :stars WHERE id = :levelId")
    suspend fun markCompleted(levelId: Int, score: Int, stars: Int)

    @Query("UPDATE levels SET isUnlocked = 1 WHERE levelNumber = :levelNumber")
    suspend fun unlockLevel(levelNumber: Int)

    @Query("SELECT COUNT(*) FROM levels")
    suspend fun count(): Int
}