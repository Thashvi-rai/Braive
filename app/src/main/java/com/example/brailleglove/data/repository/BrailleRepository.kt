package com.example.brailleglove.data.repository

import androidx.lifecycle.LiveData
import com.example.brailleglove.data.db.BrailleLetterDao
import com.example.brailleglove.data.db.LevelDao
import com.example.brailleglove.data.model.BrailleLetter
import com.example.brailleglove.data.model.Level

/**
 * Single source of truth for all data access.
 * ViewModels interact with this class only — never directly with DAOs.
 */
class BrailleRepository(
    private val letterDao: BrailleLetterDao,
    private val levelDao: LevelDao
) {

    // ── Letters ───────────────────────────────────────────────────────────────

    /** All 26 letters as LiveData — auto-updates on any DB change. */
    val allLetters: LiveData<List<BrailleLetter>> = letterDao.getAllLetters()

    /** Only letters the user has marked as learned. */
    val learnedLetters: LiveData<List<BrailleLetter>> = letterDao.getLearnedLetters()

    /** One-shot fetch of a single letter (run on IO dispatcher). */
    suspend fun getLetter(letter: Char): BrailleLetter? =
        letterDao.getLetterByChar(letter.toString())

    /**
     * Records an attempt for a letter.
     * @param correct true if the user answered correctly.
     */
    suspend fun recordAttempt(letter: Char, correct: Boolean) =
        letterDao.recordAttempt(letter.toString(), if (correct) 1 else 0)

    /** Marks a letter as learned (sets isLearned = true). */
    suspend fun markAsLearned(letter: Char) =
        letterDao.markAsLearned(letter.toString())

    /** Wipes all letter progress (used in a full reset). */
    suspend fun resetAllLetters() = letterDao.deleteAll()

    /**
     * One-shot map of every tracked letter → its accuracy stats.
     * Used to bias question generation toward letters the child struggles with.
     */
    suspend fun getLetterStatsMap(): Map<Char, BrailleLetter> =
        letterDao.getAllLettersOnce().associateBy { it.letterChar }

    // ── Levels ────────────────────────────────────────────────────────────────

    /** All 5 levels as LiveData. */
    val allLevels: LiveData<List<Level>> = levelDao.getAllLevels()

    /** Only levels the user has unlocked. */
    val unlockedLevels: LiveData<List<Level>> = levelDao.getUnlockedLevels()

    /**
     * Records a completed level attempt with score and star rating.
     * Call this at the end of a Practice session.
     */
    suspend fun markLevelCompleted(levelId: Int, score: Int, stars: Int) =
        levelDao.markCompleted(levelId, score, stars)

    /**
     * Unlocks the next level by its number (1-indexed).
     * Call this after markLevelCompleted when score >= PASS_THRESHOLD.
     */
    suspend fun unlockLevel(levelNumber: Int) =
        levelDao.unlockLevel(levelNumber)

    /** Returns the total number of levels in the DB (used as a prepopulate guard). */
    suspend fun getLevelCount(): Int = levelDao.count()

    /**
     * One-shot fetch of levels the child has already passed.
     * Their letters form the pool of "review" questions mixed into later sessions
     * so earlier letters don't get forgotten (spaced repetition across levels).
     */
    suspend fun getCompletedLevels(): List<Level> = levelDao.getCompletedLevelsOnce()
}