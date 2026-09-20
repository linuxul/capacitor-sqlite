package com.getcapacitor

import com.getcapacitor.community.database.sqlite.SQLite.UtilsSQLite
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
class ExampleUnitTest {
    private val uSqlite = UtilsSQLite()

    @Test
    fun additionIsCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun getStatementsArrayCanHandleComments() {
        val actualLines =
            arrayOf(
                "-- RedefineTables",
                "PRAGMA foreign_keys = OFF;",
                "",
                "-- CreateTable",
                "CREATE TABLE IF NOT EXISTS key_value (key TEXT NOT NULL PRIMARY KEY, VALUE TEXT);"
            )

        val expected =
            arrayOf(
                "PRAGMA foreign_keys = OFF",
                "CREATE TABLE IF NOT EXISTS key_value (key TEXT NOT NULL PRIMARY KEY, VALUE TEXT);"
            )

        assertArrayEquals(expected, uSqlite.getStatementsArray(actualLines.joinToString("\n")))
    }

    @Test
    fun getStatementsArrayCanHandleWhitespace() {
        val actualLines =
            arrayOf(
                "-- RedefineTables",
                "",
                "PRAGMA foreign_keys = OFF;",
                "",
                "-- CreateTable",
                "CREATE TABLE",
                "IF NOT EXISTS key_value",
                "--comment in the middle",
                "",
                "(key TEXT NOT NULL PRIMARY KEY, VALUE TEXT);",
                ""
            )

        val expected =
            arrayOf(
                "PRAGMA foreign_keys = OFF",
                "CREATE TABLE IF NOT EXISTS key_value (key TEXT NOT NULL PRIMARY KEY, VALUE TEXT)"
            )

        assertArrayEquals(expected, uSqlite.getStatementsArray(actualLines.joinToString("\n")))
    }
}
