package com.getcapacitor.community.database.sqlite.SQLite

import android.content.Context
import java.io.File

public class UtilsMigrate {
    private val uFile = UtilsFile()

    /** Returns null when the folder cannot be listed, as `File.list()` does. */
    public fun getMigratableList(context: Context, folderPath: String): Array<String>? {
        val pathDB = File(context.filesDir.parentFile, "databases").absolutePath
        val dirDB = File(pathDB)
        if (!dirDB.isDirectory) {
            val nDir = dirDB.mkdir()
            if (!nDir) {
                throw Exception("Cannot create dir$pathDB")
            }
        }
        val pathFiles = this.getFolder(context, folderPath)
        // check if the path exists
        val dir = File(pathFiles)
        if (!dir.exists()) {
            throw Exception("Folder $dir does not exist")
        }
        return dir.list()
    }

    public fun addSQLiteSuffix(context: Context, folderPath: String, dbList: ArrayList<String>) {
        val pathDB = File(context.filesDir.parentFile, "databases").absolutePath
        val dirDB = File(pathDB)
        if (!dirDB.isDirectory) {
            val nDir = dirDB.mkdir()
            if (!nDir) {
                throw Exception("Cannot create dir$pathDB")
            }
        }
        val pathFiles = this.getFolder(context, folderPath)
        // check if the path exists
        val dir = File(pathFiles)
        if (!dir.exists()) {
            throw Exception("Folder $dir does not exist")
        }
        // `!!`: the Java version threw a NullPointerException here when the folder could not be listed
        val listFiles = dir.list()!!
        if (pathDB != pathFiles && listFiles.isEmpty()) {
            throw Exception("Folder $dir no database files")
        }
        for (file in listFiles) {
            if (!file.contains("SQLite.db")) {
                val fromFile = file
                val toFile = suffixedName(file, dbList)
                if (toFile.isNotEmpty()) {
                    val ret = uFile.copyFromNames(context, pathFiles, fromFile, pathDB, toFile)
                    if (!ret) {
                        val msg = "Failed in copy $fromFile to $file"
                        throw Exception(msg)
                    }
                }
            }
        }
    }

    public fun getFolder(context: Context, folderPath: String): String {
        var pathFiles = context.filesDir.absolutePath
        val pathDB = File(context.filesDir.parentFile, "databases").absolutePath
        if (folderPath == "default") {
            pathFiles = pathDB
        } else if (folderPath.equals("cache", ignoreCase = true)) {
            pathFiles = context.cacheDir.absolutePath
        } else {
            val arr = folderPath.split("/", limit = 2)
            if (arr.size == 2) {
                if (arr[0] == "files") {
                    pathFiles = pathFiles + "/" + arr[1]
                } else if (arr[0] == "databases") {
                    pathFiles = pathDB + "/" + arr[1]
                } else {
                    throw Exception("Folder $folderPath not allowed")
                }
            }
        }
        return pathFiles
    }

    public fun deleteOldDatabases(context: Context, folderPath: String, dbList: ArrayList<String>) {
        val pathFiles = this.getFolder(context, folderPath)
        // check if the path exists
        val dir = File(pathFiles)
        if (!dir.exists()) {
            throw Exception("Folder $dir does not exist")
        }
        // `!!`: the Java version threw a NullPointerException here when the folder could not be listed
        val listFiles = dir.list()!!
        for (file in listFiles) {
            var delFile = ""
            if (!file.contains("SQLite.db")) {
                if (dbList.size > 0) {
                    if (dbList.contains(file)) {
                        delFile = file
                    }
                } else {
                    if (uFile.getFileExtension(file) == "db") {
                        delFile = file
                    }
                }
                if (delFile.isNotEmpty()) {
                    val ret = uFile.deleteFile(pathFiles, delFile)
                    if (!ret) {
                        val msg = "Failed in delete $delFile"
                        throw Exception(msg)
                    }
                }
            }
        }
    }

    public fun moveDatabasesAndAddSuffix(context: Context, folderPath: String, dbList: ArrayList<String>) {
        val pathDB = File(context.filesDir.parentFile, "databases").absolutePath
        val dirDB = File(pathDB)
        if (!dirDB.isDirectory) {
            val nDir = dirDB.mkdir()
            if (!nDir) {
                throw Exception("Cannot create dir$pathDB")
            }
        }
        val pathFiles = this.getFolder(context, folderPath)
        // check if the path exists
        val dir = File(pathFiles)
        if (!dir.exists()) {
            throw Exception("Folder $dir does not exist")
        }
        // `!!`: the Java version threw a NullPointerException here when the folder could not be listed
        val listFiles = dir.list()!!
        if (pathDB != pathFiles && listFiles.isEmpty()) {
            throw Exception("Folder $dir no database files")
        }
        for (file in listFiles) {
            if (file.contains("SQLite.db")) {
                continue
            }
            val fromFile = file
            val toFile = suffixedName(file, dbList)
            if (toFile.isNotEmpty()) {
                val ret = File(pathFiles, fromFile).renameTo(File(pathDB, toFile))
                if (!ret) {
                    val msg = "Failed in move $fromFile to $file"
                    throw Exception(msg)
                }
            }
        }
    }

    /** The name [file] gets in the databases folder, or "" when it is not to be migrated. */
    private fun suffixedName(file: String, dbList: ArrayList<String>): String {
        var toFile = ""
        if (dbList.size > 0) {
            if (dbList.contains(file)) {
                toFile =
                    if (uFile.getFileExtension(file) == "db") {
                        file.replace(".db", "SQLite.db")
                    } else {
                        file + "SQLite.db"
                    }
            }
        } else {
            if (uFile.getFileExtension(file) == "db") {
                toFile = file.replace(".db", "SQLite.db")
            }
        }
        return toFile
    }
}
