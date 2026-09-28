package com.example.brailleglove.data.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.brailleglove.data.model.BrailleLetter

@Dao
interface BrailleLetterDao {

    @Query("SELECT * FROM braille_letters ORDER BY letter ASC")
    fun getAllLetters(): LiveData<List<BrailleLetter>>

    @Query("SELECT * FROM braille_letters WHERE letter = :letter LIMIT 1")
    suspend fun getLetterByChar(letter: String): BrailleLetter?      // ← Char → String

    /** One-shot fetch of every letter's stats — used to build the adaptive question mix. */
    @Query("SELECT * FROM braille_letters")
    suspend fun getAllLettersOnce(): List<BrailleLetter>

    @Query("SELECT * FROM braille_letters WHERE isLearned = 1 ORDER BY letter ASC")
    fun getLearnedLetters(): LiveData<List<BrailleLetter>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLetter(letter: BrailleLetter)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllLetters(letters: List<BrailleLetter>)

    @Update
    suspend fun updateLetter(letter: BrailleLetter)

    @Query("UPDATE braille_letters SET isLearned = 1 WHERE letter = :letter")
    suspend fun markAsLearned(letter: String)                         // ← Char → String

    @Query("""
        UPDATE braille_letters 
        SET correctCount = correctCount + :correct,
            attemptCount = attemptCount + 1
        WHERE letter = :letter
    """)
    suspend fun recordAttempt(letter: String, correct: Int)           // ← Char → String

    @Query("DELETE FROM braille_letters")
    suspend fun deleteAll()
}