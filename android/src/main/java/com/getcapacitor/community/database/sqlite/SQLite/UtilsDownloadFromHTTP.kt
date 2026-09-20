package com.getcapacitor.community.database.sqlite.SQLite

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.regex.Pattern

public class UtilsDownloadFromHTTP {
    private val uFile = UtilsFile()

    public fun download(context: Context, fileUrl: String) {
        var isZip = false
        var fileName: String
        try {
            val fileDetails = getFileDetails(fileUrl)
            fileName = fileDetails[0]
            val extension = fileDetails[1]
            if (!fileName.contains("SQLite.db")) {
                when (extension) {
                    "db" -> fileName = fileName.substring(0, fileName.length - 3) + "SQLite.db"
                    "zip" -> isZip = true
                    else -> throw Exception("Unknown file type. Filename: $fileName")
                }
            }
        } catch (e: Exception) {
            throw Exception(e.message)
        }

        val cacheDir = context.cacheDir
        val cachePath = cacheDir.absolutePath
        val tmpFilePath = cachePath + File.separator + fileName
        val databasePath = uFile.getDatabaseDirectoryPath(context)
        val databaseDir = File(databasePath)
        try {
            // delete file if exists in cache
            val isExists = uFile.isPathExists(tmpFilePath)
            if (isExists) {
                uFile.deleteFile(cachePath, fileName)
            }
            downloadFileToCache(fileUrl, fileName, cachePath)
            if (isZip) {
                uFile.unzipCopyDatabase(cachePath, null, tmpFilePath, true)
                // delete zip file from cache
                uFile.deleteFile(cachePath, fileName)
            }
            // move files to database folder
            uFile.moveAllDBs(cacheDir, databaseDir)
        } catch (e: Exception) {
            throw Exception(e.message)
        }
    }

    public companion object {
        public fun getFileDetails(url: String): Array<String> {
            try {
                val javaUrl = URL(url)
                val path = javaUrl.path
                // Decode URL-encoded path
                val decodedPath = URLDecoder.decode(path, StandardCharsets.UTF_8.toString())
                // Extract filename from decoded path
                val filename = decodedPath.substring(decodedPath.lastIndexOf('/') + 1)
                val extension = getFileExtension(filename) ?: throw Exception("extension db or zip not found")
                return arrayOf(filename, extension)
            } catch (e: MalformedURLException) {
                e.printStackTrace()
                throw Exception(e.message)
            }
        }

        /** Returns the extension in lowercase, or null when there is none. */
        public fun getFileExtension(filename: String): String? {
            val pattern = Pattern.compile("\\.([a-zA-Z0-9]+)(?:[\\?#]|$)")
            val matcher = pattern.matcher(filename)

            if (matcher.find()) {
                return matcher.group(1)?.lowercase(Locale.getDefault())
            }

            return null
        }

        public fun downloadFileToCache(fileURL: String, fileName: String, cacheDir: String) {
            var httpConn: HttpURLConnection? = null
            try {
                val url = URL(fileURL)
                httpConn = url.openConnection() as HttpURLConnection
                httpConn.requestMethod = "GET"
                val responseCode = httpConn.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val contentLength = httpConn.contentLength
                    var dbName = fileName
                    if (!fileName.contains("SQLite.db")) {
                        if (fileName.substring(fileName.length - 3) == ".db") {
                            dbName = fileName.substring(0, fileName.length - 3) + "SQLite.db"
                        }
                    }

                    // create temporary file path
                    val tmpFilePath = cacheDir + File.separator + dbName
                    // opens input stream from the HTTP connection
                    httpConn.inputStream.use { inputStream ->
                        FileOutputStream(tmpFilePath).use { outputStream ->
                            val buffer = ByteArray(1024)
                            while (true) {
                                val bytesRead = inputStream.read(buffer)
                                if (bytesRead == -1) {
                                    break
                                }
                                outputStream.write(buffer, 0, bytesRead)
                            }

                            println("File $fileName downloaded ($contentLength)")
                        }
                    }
                } else {
                    val msg = "No file to download. Server replied HTTP code: $responseCode"
                    throw IOException(msg)
                }
            } catch (e: IOException) {
                throw Exception(e)
            } finally {
                httpConn?.disconnect()
            }
        }
    }
}
