package com.example.brailleglove.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.brailleglove.braille.BrailleAlphabet
import com.example.brailleglove.braille.BrailleEngine
import com.example.brailleglove.data.model.BrailleLetter
import com.example.brailleglove.data.model.Level
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [BrailleLetter::class, Level::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun brailleLetterDao(): BrailleLetterDao
    abstract fun levelDao(): LevelDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "braille_glove_db"
                )
                    .addCallback(PrepopulateCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Prepopulates the database on first creation with:
     * - All 26 BrailleLetter entries
     * - 5 default practice Levels (Level 1 unlocked)
     */
    private class PrepopulateCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    prepopulateLetters(database.brailleLetterDao())
                    prepopulateLevels(database.levelDao())
                }
            }
        }

        private suspend fun prepopulateLetters(dao: BrailleLetterDao) {
            val letters = BrailleAlphabet.alphabet.map { (char, _) ->
                BrailleLetter(
                    letter           = char.toString(),
                    dotDescription   = BrailleEngine.describeDots(char),
                    dotPatternString = BrailleAlphabet.patternFor(char)
                        ?.joinToString("") { if (it) "1" else "0" } ?: "000000"
                )
            }
            dao.insertAllLetters(letters)
        }

        private suspend fun prepopulateLevels(dao: LevelDao) {
            if (dao.count() == 0) {
                dao.insertAllLevels(Level.defaultLevels())
            }
        }
    }
}