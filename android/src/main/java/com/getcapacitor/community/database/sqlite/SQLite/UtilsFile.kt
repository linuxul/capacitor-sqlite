package com.getcapacitor.community.database.sqlite.SQLite

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.FileChannel
import java.util.zip.ZipInputStream

public class UtilsFile {
    public fun isFileExists(context: Context, dbName: String): Boolean {
        val file = context.getDatabasePath(dbName)
        return file.exists()
    }

    public fun isPathExists(filePath: String): Boolean {
        val file = File(filePath)
        return file.exists()
    }

    public fun getDatabaseDirectoryPath(context: Context?): String? =
        if (context != null && context.getDatabasePath("x") != null) context.getDatabasePath("x").parent else ""

    public fun getListOfFiles(context: Context): Array<String> {
        val files = context.databaseList()
        val dbs: MutableList<String> = ArrayList()
        for (file in files) {
            if (file.endsWith("SQLite.db")) {
                dbs.add(file)
            }
        }
        return dbs.toTypedArray()
    }

    public fun deleteDatabase(context: Context, dbName: String): Boolean {
        context.deleteDatabase(dbName)
        return !isFileExists(context, dbName)
    }

    public fun deleteFile(context: Context, dbName: String): Boolean {
        val file = context.getDatabasePath(dbName)
        return file.delete()
    }

    public fun deleteFile(filePath: String, dbName: String): Boolean {
        val file = File(filePath, dbName)
        return file.delete()
    }

    public fun deleteFile(file: File): Boolean = file.delete()

    public fun copyFromAssetsToDatabase(context: Context, overwrite: Boolean) {
        val assetManager = context.assets
        val assetsDatabasePath = "public/assets/databases"
        try {
            // check if databases directory exists else create it
            val pathDB = File(context.filesDir.parentFile, "databases").absolutePath
            val dirDB = File(pathDB)
            if (!dirDB.isDirectory) {
                val nDir = dirDB.mkdir()
                if (!nDir) {
                    throw Exception("Cannot create dir$pathDB")
                }
            }

            // look into the public/assets/databases to get databases to copy
            // !! as in Java, which dereferenced the list without a null check
            val filelist = assetManager.list(assetsDatabasePath)!!
            if (filelist.isEmpty()) {
                // dir does not exist or is not a directory
                throw Exception("Folder public/assets/databases does not exist or is empty")
            } else {
                for (fileName in filelist) {
                    // Get filename of file or directory
                    if (isLast(fileName, ".db")) {
                        val toFileName = addSQLiteSuffix(fileName)
                        val isExist = isFileExists(context, toFileName)
                        if (!isExist || overwrite) {
                            if (overwrite && isExist) {
                                deleteDatabase(context, toFileName)
                            }
                            val fromPathName = "$assetsDatabasePath/$fileName"
                            val toPathName = context.getDatabasePath(toFileName).absolutePath
                            copyDatabaseFromAssets(assetManager, fromPathName, toPathName)
                        }
                    }
                    if (isLast(fileName, ".zip")) {
                        // unzip file and extract databases
                        val zipPathName = "$assetsDatabasePath/$fileName"
                        val databasePath = getDatabaseDirectoryPath(context)
                        unzipCopyDatabase(databasePath, assetManager, zipPathName, overwrite)
                    }
                }
                return
            }
        } catch (e: IOException) {
            throw Exception("in copyFromAssetsToDatabase " + e.localizedMessage)
        }
    }

    public fun unzipCopyDatabase(databasePath: String?, asm: AssetManager?, zipPath: String, overwrite: Boolean) {
        var `is`: InputStream? = null
        var isF: FileInputStream? = null
        val zis: ZipInputStream
        val buffer = ByteArray(1024)

        try {
            if (asm != null) {
                `is` = asm.open(zipPath)
                zis = ZipInputStream(`is`)
            } else {
                val zipFile = File(zipPath)
                isF = FileInputStream(zipFile)
                zis = ZipInputStream(isF)
            }
            var ze = zis.nextEntry
            // As in Java, the next entry is only fetched for a ".db" entry
            while (ze != null) {
                val fileName = ze.name
                if (isLast(fileName, ".db")) {
                    val toFileName = addSQLiteSuffix(fileName)
                    val dbPath = databasePath + File.separator + toFileName
                    val isExist = isPathExists(dbPath)
                    if (!isExist || overwrite) {
                        if (overwrite && isExist) {
                            // databasePath was dereferenced by Java's File(String, String) too
                            deleteFile(databasePath!!, toFileName)
                        }
                        val newFile = File(dbPath)
                        println("Unzipping to " + newFile.absolutePath)
                        val fos = FileOutputStream(newFile)
                        var len: Int
                        while (zis.read(buffer).also { len = it } > 0) {
                            fos.write(buffer, 0, len)
                        }
                        fos.close()
                    }
                    // close this ZipEntry
                    zis.closeEntry()
                    ze = zis.nextEntry
                }
            }
            // close last ZipEntry
            zis.closeEntry()
            zis.close()
            if (asm != null && `is` != null) {
                `is`.close()
            } else if (isF != null) {
                isF.close()
            }
        } catch (e: IOException) {
            throw IOException("in unzipCopyDatabase " + e.localizedMessage)
        }
    }

    public fun addSQLiteSuffix(fileName: String): String {
        var toFileName = fileName
        val isSQLite = isLast(fileName, "SQLite.db")
        if (!isSQLite) {
            toFileName = fileName.substring(0, fileName.length - 3) + "SQLite.db"
        }
        return toFileName
    }

    public fun copyDatabaseFromAssets(asm: AssetManager, inPath: String, outPath: String) {
        try {
            val buffer = ByteArray(1024)
            var length: Int
            val sInput = asm.open(inPath)
            val sOutput: OutputStream = FileOutputStream(outPath)
            while (sInput.read(buffer).also { length = it } > 0) {
                sOutput.write(buffer, 0, length)
            }
            sOutput.close()
            sOutput.flush()
            sInput.close()
            return
        } catch (e: IOException) {
            throw IOException("in copyDatabaseFromAssets " + e.localizedMessage)
        }
    }

    public fun isLast(filename: String, ext: String): Boolean {
        val nExt = ext.length
        if (filename.length <= nExt) return false
        val last = filename.substring(filename.length - nExt)
        return last == ext
    }

    public fun renameFile(context: Context, dbName: String, toDbName: String): Boolean {
        val file = context.getDatabasePath(dbName)
        val toFile = context.getDatabasePath(toDbName)
        return file.renameTo(toFile)
    }

    public fun copyFile(context: Context, dbName: String, toDbName: String): Boolean {
        val file = context.getDatabasePath(dbName)
        val toFile = context.getDatabasePath(toDbName)

        try {
            if (!toFile.exists()) {
                val cFile = toFile.createNewFile()
                if (!cFile) {
                    throw Exception("Cannot create file$dbName")
                }
            }
            copyFileFromFile(file, toFile)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error: in copyFile $e")
            return false
        }
    }

    public fun copyFromNames(context: Context, fromPath: String, fromName: String, toPath: String?, toName: String): Boolean {
        val fromFile = File(fromPath, fromName)
        val toFile = context.getDatabasePath(toName)
        try {
            if (!toFile.exists()) {
                val cFile = toFile.createNewFile()
                if (!cFile) {
                    Log.e(TAG, "Error: in toFile $toName")
                    return false
                }
            }
            copyFileFromFile(fromFile, toFile)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error: in copyFile $e")
            return false
        }
    }

    public fun getFileExtension(name: String): String = if (name.lastIndexOf(".") != -1 && name.lastIndexOf(".") != 0) {
        name.substring(name.lastIndexOf(".") + 1)
    } else {
        ""
    }

    public fun restoreDatabase(context: Context, databaseName: String): Boolean {
        // check if the backup file exists
        val isBackup = isFileExists(context, "backup-$databaseName")
        if (isBackup) {
            // check if database exists
            val isDB = isFileExists(context, databaseName)
            if (isDB) {
                var retD = deleteFile(context, databaseName)
                if (!retD) {
                    var msg = "Error: restoreDatabase: delete file "
                    msg += databaseName
                    Log.e(TAG, msg)
                    return false
                } else {
                    val retC = copyFile(context, "backup-$databaseName", databaseName)
                    if (!retC) {
                        var msg = "Error: restoreDatabase: copy file "
                        msg += databaseName
                        Log.e(TAG, msg)
                        return false
                    }
                    retD = deleteFile(context, "backup-$databaseName")
                    if (!retD) {
                        var msg = "Error: restoreDatabase: delete file "
                        msg += "backup-$databaseName"
                        Log.e(TAG, msg)
                        return false
                    }
                    return true
                }
            } else {
                var msg = "Error: restoreDatabase: database "
                msg += databaseName + "does not exists"
                Log.e(TAG, msg)
                return false
            }
        } else {
            var msg = "Error: restoreDB: backup-$databaseName"
            msg += " does not exist"
            Log.e(TAG, msg)
            return false
        }
    }

    public fun deleteBackupDB(context: Context, databaseName: String): Boolean {
        // check if the backup file exists
        val isBackup = isFileExists(context, "backup-$databaseName")
        if (isBackup) {
            val retD = deleteFile(context, "backup-$databaseName")
            if (!retD) {
                var msg = "Error: deleteBackupDB: delete file "
                msg += "backup-$databaseName"
                Log.e(TAG, msg)
                return false
            }
            return true
        } else {
            var msg = "Error: deleteBackupDB: backup-$databaseName"
            msg += " does not exist"
            Log.e(TAG, msg)
            return false
        }
    }

    public fun moveAllDBs(fromDir: File, toDir: File) {
        // get the file List from fromDir
        val fromDirPath = fromDir.absolutePath
        val toDirPath = toDir.absolutePath

        try {
            val fileList = listDatabases(fromDir)
            for (fileName in fileList) {
                // Check if the file exists in toDir
                val toFile = File(toDirPath, fileName)
                val isPath = isPathExists(toFile.absolutePath)
                if (isPath) {
                    deleteFile(toFile)
                }
                val fromFile = File(fromDirPath, fileName)

                val success = fromFile.renameTo(toFile)
                if (!success) {
                    throw Exception("moveAllDBs: move file $fileName failed")
                }
            }
        } catch (e: Exception) {
            throw Exception(e.message)
        }
    }

    public fun listDatabases(fileDir: File): MutableList<String> {
        val fileList: MutableList<String> = ArrayList()
        if (!fileDir.exists()) {
            throw Exception("File " + fileDir.absolutePath + " does not exist")
        }
        if (!fileDir.isDirectory) {
            throw Exception("File " + fileDir.absolutePath + " is not a directory")
        }
        // !! as in Java, which iterated the result without a null check
        val fList = fileDir.listFiles()!!
        for (file in fList) {
            if (file.isFile) {
                val fileName = file.name
                if (getFileExtension(fileName) == "db") {
                    fileList.add(fileName)
                }
            }
        }
        return fileList
    }

    private companion object {
        private val TAG: String = UtilsFile::class.java.name

        private fun copyFileFromFile(sourceFile: File, destFile: File) {
            // !! as in Java, which dereferenced the parent without a null check
            val parent = destFile.parentFile!!
            if (!parent.exists()) {
                val mDir = parent.mkdirs()
                if (!mDir) {
                    val message = "failed in creating directory"
                    throw IOException(message)
                }
            }

            if (!destFile.exists()) {
                val cFile = destFile.createNewFile()
                if (!cFile) {
                    val message = "failed in creating new file"
                    throw IOException(message)
                }
            }

            var source: FileChannel? = null
            var destination: FileChannel? = null

            try {
                source = FileInputStream(sourceFile).channel
                destination = FileOutputStream(destFile).channel
                destination.transferFrom(source, 0, source.size())
            } finally {
                source?.close()
                destination?.close()
            }
        }
    }
}
