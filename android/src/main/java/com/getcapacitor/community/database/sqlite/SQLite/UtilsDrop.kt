package com.getcapacitor.community.database.sqlite.SQLite

import android.util.Log
import java.util.Dictionary
import net.zetetic.database.sqlcipher.SQLiteCursor

public class UtilsDrop {
    /**
     * Get all Table's name
     * @param db
     * @return List<String>
     */
    public fun getTablesNames(db: Database): MutableList<String> {
        var query = "SELECT name FROM sqlite_master WHERE "
        query += "type='table' AND name NOT LIKE 'sync_table' "
        query += "AND name NOT LIKE '_temp_%' "
        query += "AND name NOT LIKE 'sqlite_%' "
        query += "AND name NOT LIKE 'android_%' "
        query += "ORDER BY rootpage DESC;"
        return getNamesIgnoringErrors(db, query)
    }

    /**
     * Get all view's name
     * @param db
     * @return List<String>
     */
    public fun getViewNames(db: Database): MutableList<String> {
        var query = "SELECT name FROM sqlite_master WHERE "
        query += "type='view' AND name NOT LIKE 'sqlite_%' "
        query += "ORDER BY rootpage DESC;"
        return getNamesIgnoringErrors(db, query)
    }

    /**
     * The Java version returned from its finally block, which discarded whatever was thrown while reading the
     * cursor: a failure half way through yields the names read so far. Only a query that produced no cursor at
     * all fails, with the NullPointerException of closing it.
     */
    private fun getNamesIgnoringErrors(db: Database, query: String): MutableList<String> {
        val names: MutableList<String> = ArrayList()
        var cursor: SQLiteCursor? = null
        try {
            // !! as in Java, where a database that is not open threw a NullPointerException here
            cursor = db.db!!.query(query) as SQLiteCursor
            cursor.moveToFirst()
            while (!cursor.isAfterLast) {
                names.add(cursor.getString(0))
                cursor.moveToNext()
            }
        } catch (_: Throwable) {
            // discarded, see above
        }
        // !! keeps the NullPointerException of the Java finally block
        cursor!!.close()
        return names
    }

    /**
     * Drop all Tables
     * @param db
     */
    public fun dropTables(db: Database) {
        try {
            val tables = getTablesNames(db)
            for (tableName in tables) {
                db.db!!.execSQL("DROP TABLE IF EXISTS $tableName ;")
            }
        } catch (e: Exception) {
            val msg = "DropAllTables failed: $e"
            Log.d(TAG, msg)
            throw Exception(msg)
        }
    }

    /**
     * Drop all Views
     * @param db
     */
    public fun dropViews(db: Database) {
        try {
            val views = getViewNames(db)
            for (viewName in views) {
                db.db!!.execSQL("DROP VIEW IF EXISTS $viewName ;")
            }
        } catch (e: Exception) {
            val msg = "DropAllViews failed: $e"
            Log.d(TAG, msg)
            throw Exception(msg)
        }
    }

    /**
     * get all index's name
     * @param db
     * @return List<String>
     */
    public fun getIndexesNames(db: Database): MutableList<String> {
        var query = "SELECT name FROM sqlite_master WHERE "
        query += "type='index' AND name NOT LIKE 'sqlite_%';"
        return getNames(db, query)
    }

    /**
     * Drop all Indexes
     * @param db
     */
    public fun dropIndexes(db: Database) {
        val indexes = getIndexesNames(db)
        try {
            for (indexName in indexes) {
                db.db!!.execSQL("DROP INDEX IF EXISTS $indexName")
            }
        } catch (e: Exception) {
            val msg = "DropAllIndexes failed: $e"
            Log.d(TAG, msg)
            throw Exception(msg)
        }
    }

    /**
     * get all trigger's name
     * @param db
     * @return List<String>
     */
    public fun getTriggersNames(db: Database): MutableList<String> {
        var query = "SELECT name FROM sqlite_master WHERE "
        query += "type='trigger';"
        return getNames(db, query)
    }

    private fun getNames(db: Database, query: String): MutableList<String> {
        val names: MutableList<String> = ArrayList()
        // !! as in Java, where a database that is not open threw a NullPointerException here
        val cursor = db.db!!.query(query) as SQLiteCursor
        cursor.moveToFirst()
        while (!cursor.isAfterLast) {
            names.add(cursor.getString(0))
            cursor.moveToNext()
        }
        cursor.close()
        return names
    }

    /**
     * Drop all Triggers
     * @param db
     */
    public fun dropTriggers(db: Database) {
        val triggers = getTriggersNames(db)
        try {
            for (triggerName in triggers) {
                db.db!!.execSQL("DROP TRIGGER IF EXISTS $triggerName")
            }
        } catch (e: Exception) {
            val msg = "DropAllTriggers failed: $e"
            Log.d(TAG, msg)
            throw Exception(msg)
        }
    }

    /**
     * Drop all
     * @param db
     */
    public fun dropAll(db: Database) {
        var success = false
        try {
            db.beginTransaction()
            dropTables(db)
            dropIndexes(db)
            dropTriggers(db)
            dropViews(db)
            db.commitTransaction()
            success = true
        } catch (e: Exception) {
            val msg = "DropAll failed: $e"
            Log.d(TAG, msg)
            throw Exception(msg)
        } finally {
            if (success) db.rollbackTransaction()
            try {
                db.db!!.execSQL("VACUUM;")
            } catch (e: Exception) {
                val msg = "DropAll VACUUM failed: $e"
                Log.d(TAG, msg)
                throw Exception(msg)
            }
        }
    }

    /**
     * Drop Temporary Tables
     *
     * @param db
     * @param alterTables
     * @throws Exception
     */
    public fun dropTempTables(db: Database, alterTables: Dictionary<String, List<String>>) {
        try {
            val tables = this.getDictStringKeys(alterTables)
            val statements: MutableList<String> = ArrayList()
            for (table in tables) {
                val stmt = "DROP TABLE IF EXISTS _temp_$table;"
                statements.add(stmt)
            }
            if (statements.size > 0) {
                val retObj = db.execute(statements.toTypedArray())
                // !! as in Java, which unboxed the value and so threw when "changes" was missing
                val changes: Int = retObj.getInteger("changes")!!
                if (changes < 0) {
                    throw Exception("DropTempTables failed")
                }
            }
        } catch (e: Exception) {
            val msg = "DropTempTables failed: $e"
            Log.d(TAG, msg)
            throw Exception(msg)
        }
    }

    /**
     * getDictStringKeys
     *
     * @param dict
     * @return
     */
    public fun getDictStringKeys(dict: Dictionary<String, List<String>>): MutableList<String> {
        val lkeys: MutableList<String> = ArrayList()
        val keys = dict.keys()
        while (keys.hasMoreElements()) {
            lkeys.add(keys.nextElement())
        }
        return lkeys
    }

    private companion object {
        private val TAG: String = UtilsDrop::class.java.name
    }
}
