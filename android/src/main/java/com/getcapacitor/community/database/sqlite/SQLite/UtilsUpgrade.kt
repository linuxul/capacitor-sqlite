package com.getcapacitor.community.database.sqlite.SQLite

import android.util.Log
import java.util.Collections
import java.util.Dictionary
import org.json.JSONArray
import org.json.JSONObject

public class UtilsUpgrade {
    /**
     * OnUpgrade Method
     * Database version upgrade flow process
     *
     * @param db
     * @param upgDict
     * @param curVersion
     * @param targetVersion
     * @throws Exception
     */
    public fun onUpgrade(db: Database, upgDict: Dictionary<Int, JSONObject>, curVersion: Int, targetVersion: Int) {
        Log.i(TAG, "UtilsUpgrade.onUpgrade: from $curVersion to $targetVersion")

        val sortedKeys = Collections.list(upgDict.keys())
        sortedKeys.sort()

        for (versionKey in sortedKeys) {
            if (versionKey > curVersion && versionKey <= targetVersion) {
                Log.i(TAG, "- UtilsUpgrade.onUpgrade toVersion: $versionKey")
                val upgrade = upgDict.get(versionKey)

                val statementsJson = if (upgrade.has("statements")) upgrade.getJSONArray("statements") else JSONArray()

                val statements = ArrayList<String>()

                for (i in 0 until statementsJson.length()) {
                    statements.add(statementsJson.getString(i))
                }

                if (statements.size == 0) {
                    val msg = "Error: onUpgrade statement not given"
                    throw Exception(msg)
                }

                try {
                    executeStatementsProcess(db, statements.toTypedArray())

                    // Java dereferenced the database without a null check
                    db.db!!.version = versionKey
                } catch (e: Exception) {
                    var msg = "Error: onUpgrade executeStatementProcess"
                    msg += " failed $e"
                    throw Exception(msg)
                }
            }
        }
    }

    /**
     * ExecuteStatementsProcess Method
     * Execute Statement Flow Process
     *
     * @param db
     * @param statements
     * @throws Exception
     */
    private fun executeStatementsProcess(db: Database, statements: Array<String>) {
        db.beginTransaction()
        try {
            db.execute(statements, false)

            db.commitTransaction()
        } catch (e: Exception) {
            throw Exception("Error: executeStatementsProcess " + " failed " + e)
        } finally {
            db.rollbackTransaction()
        }
    }

    private companion object {
        private val TAG: String = UtilsUpgrade::class.java.name
    }
}
