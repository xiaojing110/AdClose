package com.close.hook.ads.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.close.hook.ads.data.dao.RuleSubscriptionDao
import com.close.hook.ads.data.dao.UrlDao
import com.close.hook.ads.data.model.RuleSubscription
import com.close.hook.ads.data.model.Url

@Database(entities = [Url::class, RuleSubscription::class], version = 5, exportSchema = false)
abstract class UrlDatabase : RoomDatabase() {
    abstract val urlDao: UrlDao
    abstract val ruleSubscriptionDao: RuleSubscriptionDao

    companion object {
        @Volatile
        private var instance: UrlDatabase? = null

        private val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE url_info_new (id INTEGER NOT NULL, url TEXT NOT NULL, PRIMARY KEY(id))")
                db.execSQL("INSERT INTO url_info_new (id, url) SELECT id, url FROM url_info")
                db.execSQL("DROP TABLE url_info")
                db.execSQL("ALTER TABLE url_info_new RENAME TO url_info")
            }
        }

        private val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE url_info ADD COLUMN type TEXT NOT NULL DEFAULT 'url'")
            }
        }

        private val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_url_info_url ON url_info(url)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_url_info_type ON url_info(type)")
            }
        }

        private val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS rule_subscription (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        url TEXT NOT NULL,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        is_builtin INTEGER NOT NULL DEFAULT 0,
                        last_update INTEGER NOT NULL DEFAULT 0,
                        update_interval_hours INTEGER NOT NULL DEFAULT 12,
                        rule_count INTEGER NOT NULL DEFAULT 0,
                        status TEXT NOT NULL DEFAULT 'idle'
                    )
                """)
            }
        }

        fun getDatabase(context: Context): UrlDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    UrlDatabase::class.java,
                    "url_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build().also {
                    instance = it
                }
            }
    }
}
