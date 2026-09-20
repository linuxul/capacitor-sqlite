package com.getcapacitor.community.database.sqlite.SQLite

import android.content.Context
import java.io.File

public class UtilsNCDatabase {
    private val uMigrate = UtilsMigrate()

    public fun getNCDatabasePath(context: Context, folderPath: String, database: String): String {
        val pathDB = File(context.filesDir.parentFile, "databases").absolutePath
        val dirDB = File(pathDB)
        if (!dirDB.isDirectory) {
            val nDir = dirDB.mkdir()
            if (!nDir) {
                throw Exception("Cannot create dir$pathDB")
            }
        }
        val pathFiles = uMigrate.getFolder(context, folderPath)
        // check if the path exists
        val dir = File(pathFiles)
        if (!dir.exists()) {
            throw Exception("Folder $dir does not exist")
        }
        return "$pathFiles/$database"
    }
}
