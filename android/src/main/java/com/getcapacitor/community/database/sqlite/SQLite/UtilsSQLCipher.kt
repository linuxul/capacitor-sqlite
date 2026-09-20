package com.getcapacitor.community.database.sqlite.SQLite

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.io.FileNotFoundException
import java.nio.charset.Charset
import net.zetetic.database.sqlcipher.SQLiteDatabase

public class UtilsSQLCipher {
    /**
     * The detected state of the database, based on whether we can
     * open it without a passphrase, with the passphrase 'secret'.
     */
    public enum class State {
        DOES_NOT_EXIST,
        UNENCRYPTED,
        ENCRYPTED_SECRET,
        ENCRYPTED_GLOBAL_SECRET,
        UNKNOWN
    }

    /**
     * Determine whether or not this database appears to be encrypted,
     * based on whether we can open it without a passphrase or with
     * the passphrase 'secret'.
     *
     * @param dbPath a File pointing to the database
     * @param sharedPreferences an instance of SharedPreferences
     * @param globVar an instance of GlobalSQLite
     * @return the detected state of the database
     */
    @Suppress("UNUSED_PARAMETER")
    public fun getDatabaseState(ctxt: Context?, dbPath: File, sharedPreferences: SharedPreferences?, globVar: GlobalSQLite): State {
        System.loadLibrary("sqlcipher")
        if (dbPath.exists()) {
            var db: SQLiteDatabase? = null

            try {
                db = SQLiteDatabase.openDatabase(dbPath.absolutePath, "", null, SQLiteDatabase.OPEN_READONLY, null)

                db.version

                return State.UNENCRYPTED
            } catch (e: Exception) {
                try {
                    // Without encryption there are no shared preferences; as in the Java version the resulting
                    // NullPointerException moves on to the global secret.
                    val passphrase = sharedPreferences!!.getString("secret", "")!!
                    if (passphrase.isNotEmpty()) {
                        db = SQLiteDatabase.openDatabase(dbPath.absolutePath, passphrase, null, SQLiteDatabase.OPEN_READONLY, null)
                        db.version
                        return State.ENCRYPTED_SECRET
                    } else {
                        return State.UNKNOWN
                    }
                } catch (e1: Exception) {
                    try {
                        if (globVar.secret.isNotEmpty()) {
                            db =
                                SQLiteDatabase.openDatabase(
                                    dbPath.absolutePath,
                                    globVar.secret,
                                    null,
                                    SQLiteDatabase.OPEN_READONLY,
                                    null
                                )
                            db.version
                            return State.ENCRYPTED_GLOBAL_SECRET
                        } else {
                            return State.UNKNOWN
                        }
                    } catch (e2: Exception) {
                        return State.UNKNOWN
                    }
                }
            } finally {
                db?.close()
            }
        }

        return State.DOES_NOT_EXIST
    }

    /**
     * Replaces this database with a version encrypted with the supplied
     * passphrase, deleting the original.
     * Do not call this while the database is open.
     *
     * The passphrase is untouched in this call.
     *
     * @param ctxt a Context
     * @param originalFile a File pointing to the database
     * @param passphrase the passphrase from the user
     * @throws java.io.IOException
     */
    public fun encrypt(ctxt: Context, originalFile: File, passphrase: ByteArray) {
        System.loadLibrary("sqlcipher")

        if (originalFile.exists()) {
            val newFile = File.createTempFile("sqlcipherutils", "tmp", ctxt.cacheDir)
            var db = SQLiteDatabase.openDatabase(originalFile.absolutePath, "", null, SQLiteDatabase.OPEN_READWRITE, null)
            val version = db.version

            db.close()

            db = SQLiteDatabase.openDatabase(newFile.absolutePath, passphrase, null, SQLiteDatabase.OPEN_READWRITE, null, null)
            val st = db.compileStatement("ATTACH DATABASE ? AS plaintext KEY '';")

            st.bindString(1, originalFile.absolutePath)
            st.execute()

            db.rawExecSQL("SELECT sqlcipher_export('main', 'plaintext');")
            db.rawExecSQL("DETACH DATABASE plaintext;")

            db.version = version
            st.close()
            db.close()

            val delFile = originalFile.delete()
            if (!delFile) {
                throw FileNotFoundException(originalFile.absolutePath + " not deleted")
            }
            val renFile = newFile.renameTo(originalFile)
            if (!renFile) {
                throw FileNotFoundException(originalFile.absolutePath + " not renamed")
            }
        } else {
            throw FileNotFoundException(originalFile.absolutePath + " not found")
        }
    }

    public fun decrypt(ctxt: Context, originalFile: File, passphrase: ByteArray) {
        System.loadLibrary("sqlcipher")

        if (originalFile.exists()) {
            // Create a temporary file for the decrypted database in the cache directory
            val decryptedFile = File.createTempFile("sqlcipherutils", "tmp", ctxt.cacheDir)

            // Open the decrypted database
            val decryptedDb = SQLiteDatabase.openDatabase(decryptedFile.absolutePath, "", null, SQLiteDatabase.OPEN_READWRITE, null)

            // Open the encrypted database with the provided passphrase
            val encryptedDb =
                SQLiteDatabase.openDatabase(
                    originalFile.absolutePath,
                    // Java's `new String(bytes)`: the platform default charset
                    String(passphrase, Charset.defaultCharset()),
                    null,
                    SQLiteDatabase.OPEN_READWRITE,
                    null
                )

            val version = encryptedDb.version
            decryptedDb.version = version

            decryptedDb.close()

            // Attach the encrypted database to itself using an empty key
            val attachStatement = encryptedDb.compileStatement("ATTACH DATABASE ? AS plaintext KEY '';")

            attachStatement.bindString(1, decryptedFile.absolutePath)
            attachStatement.execute()

            // Export data from the encrypted database to the plaintext database
            encryptedDb.rawExecSQL("SELECT sqlcipher_export('plaintext');")

            // Detach the plaintext database
            encryptedDb.rawExecSQL("DETACH DATABASE plaintext;")

            attachStatement.close()
            encryptedDb.close()

            val delFile = originalFile.delete()
            if (!delFile) {
                throw FileNotFoundException(originalFile.absolutePath + " not deleted")
            }
            val renFile = decryptedFile.renameTo(originalFile)
            if (!renFile) {
                throw FileNotFoundException(originalFile.absolutePath + " not renamed")
            }
        } else {
            throw FileNotFoundException(originalFile.absolutePath + " not found")
        }
    }

    @Suppress("UNUSED_PARAMETER")
    public fun changePassword(ctxt: Context?, file: File, password: String, nwpassword: String) {
        System.loadLibrary("sqlcipher")

        if (file.exists()) {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, password, null, SQLiteDatabase.OPEN_READWRITE, null)

            if (!db.isOpen) {
                throw Exception("database " + file.absolutePath + " open failed")
            }
            db.changePassword(nwpassword)
            db.close()
        } else {
            throw FileNotFoundException(file.absolutePath + " not found")
        }
    }
}
