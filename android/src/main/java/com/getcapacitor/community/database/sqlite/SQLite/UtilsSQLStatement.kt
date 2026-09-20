package com.getcapacitor.community.database.sqlite.SQLite

import java.util.Locale
import java.util.regex.Pattern

public object UtilsSQLStatement {
    /** Same as Java's `String.split(regex)`: the separator is a regular expression and trailing empty strings are dropped. */
    private fun String.javaSplit(regex: String): Array<String> = Pattern.compile(regex).split(this)

    public fun flattenMultilineString(input: String): String {
        val lines = input.javaSplit("\\r?\\n")
        return lines.joinToString(" ")
    }

    public fun extractTableName(statement: String): String? {
        val pattern = Pattern.compile("(?:INSERT\\s+INTO|UPDATE|DELETE\\s+FROM)\\s+([^\\s]+)", Pattern.CASE_INSENSITIVE)
        val match = pattern.matcher(statement)
        if (match.find() && match.groupCount() > 0) {
            return match.group(1)
        }
        return null
    }

    public fun extractWhereClause(statement: String): String? {
        val pattern = Pattern.compile("WHERE(.+?)(?:ORDER\\s+BY|LIMIT|$)", Pattern.CASE_INSENSITIVE)
        val match = pattern.matcher(statement)
        if (match.find() && match.groupCount() > 0) {
            return match.group(1).trim { it <= ' ' }
        }
        return null
    }

    public fun addPrefixToWhereClause(whereClause: String, colNames: Array<String>, refNames: Array<String>, prefix: String): String {
        var columnValuePairs: Array<String>? = null
        val logicalOperators = arrayOf("AND", "OR", "NOT")

        for (logicalOperator in logicalOperators) {
            if (whereClause.contains(logicalOperator)) {
                columnValuePairs = whereClause.javaSplit("\\s*$logicalOperator\\s*")
                break
            }
        }

        if (columnValuePairs == null) {
            columnValuePairs = arrayOf(whereClause)
        }

        val modifiedPairs: MutableList<String> = ArrayList()

        for (pair in columnValuePairs) {
            val trimmedPair = pair.trim { it <= ' ' }

            var operatorIndex = -1
            var operator: String? = null
            for (op in arrayOf("=", "<>", "<", "<=", ">", ">=", "IN", "BETWEEN", "LIKE")) {
                operatorIndex = trimmedPair.indexOf(op)
                if (operatorIndex != -1) {
                    operator = op
                    break
                }
            }

            if (operator == null) {
                modifiedPairs.add(trimmedPair)
                continue
            }

            val column = trimmedPair.substring(0, operatorIndex).trim { it <= ' ' }
            val value = trimmedPair.substring(operatorIndex + operator.length).trim { it <= ' ' }

            var newColumn: String? = column
            val index = findIndexOfStringInArray(column, refNames)
            if (index != -1) {
                newColumn = getStringAtIndex(colNames, index)
            }

            // A null column is rendered as "null", as Java's string concatenation did
            val modifiedColumn = prefix + newColumn
            val modifiedPair = "$modifiedColumn $operator $value"
            modifiedPairs.add(modifiedPair)
        }

        var logicalOperatorUsed = logicalOperators[0]
        for (logicalOperator in logicalOperators) {
            if (whereClause.contains(logicalOperator)) {
                logicalOperatorUsed = logicalOperator
                break
            }
        }
        return modifiedPairs.joinToString(" $logicalOperatorUsed ")
    }

    public fun findIndexOfStringInArray(target: String?, array: Array<String>): Int {
        for (i in array.indices) {
            if (array[i] == target) {
                return i
            }
        }
        return -1
    }

    public fun getStringAtIndex(array: Array<String>, index: Int): String? = if (index >= 0 && index < array.size) {
        array[index]
    } else {
        null
    }

    public fun extractForeignKeyInfo(sqlStatement: String): UtilsDelete.ForeignKeyInfo {
        // Define the regular expression pattern for extracting the FOREIGN KEY clause
        val foreignKeyPattern =
            "\\bFOREIGN\\s+KEY\\s*\\(([^)]+)\\)\\s+REFERENCES\\s+(\\w+)\\s*\\(([^)]+)\\)\\s+" +
                "(ON\\s+DELETE\\s+(RESTRICT|CASCADE|SET\\s+NULL|SET\\s+DEFAULT|NO\\s+ACTION))?"
        val pattern = Pattern.compile(foreignKeyPattern)
        val matcher = pattern.matcher(sqlStatement)

        if (matcher.find()) {
            val forKeys = matcher.group(1).javaSplit(",")
            val tableName = matcher.group(2)
            val refKeys = matcher.group(3).javaSplit(",")
            val action = matcher.group(5) ?: "NO ACTION"
            val lForKeys: MutableList<String> = ArrayList(forKeys.asList())
            val lRefKeys: MutableList<String> = ArrayList(refKeys.asList())
            return UtilsDelete.ForeignKeyInfo(lForKeys, tableName, lRefKeys, action)
        } else {
            throw Exception("extractForeignKeyInfo: No FOREIGN KEY found")
        }
    }

    public fun extractColumnNames(whereClause: String): MutableList<String> {
        val keywords = hashSetOf("AND", "OR", "IN", "VALUES", "LIKE", "BETWEEN", "NOT")

        val pattern =
            Pattern.compile(
                "\\b[a-zA-Z]\\w*\\b(?=\\s*(?:<=?|>=?|<>?|=|AND|OR|BETWEEN|NOT|IN|LIKE))|" +
                    "\\b[a-zA-Z]\\w*\\b\\s+BETWEEN\\s+'[^']+'\\s+AND\\s+'[^']+'|" +
                    "\\(([^)]+)\\)\\s+IN\\s+\\(\\s*VALUES\\s*\\("
            )
        val matcher = pattern.matcher(whereClause)
        val columns: MutableList<String> = ArrayList()

        while (matcher.find()) {
            val columnList: String? = matcher.group(1)
            if (columnList != null) {
                val columnNamesArray = columnList.javaSplit(",")
                for (columnName in columnNamesArray) {
                    columns.add(columnName.trim { it <= ' ' })
                }
            } else {
                val matchedText = matcher.group()
                if (!keywords.contains(matchedText.trim { it <= ' ' }.uppercase(Locale.getDefault()))) {
                    columns.add(matchedText.trim { it <= ' ' })
                }
            }
        }

        return columns
    }

    public fun indicesOf(str: String, searchStr: String, fromIndex: Int): MutableList<Int> {
        val indices: MutableList<Int> = ArrayList()

        var currentIndex = str.indexOf(searchStr, fromIndex)
        while (currentIndex != -1) {
            indices.add(currentIndex)
            currentIndex = str.indexOf(searchStr, currentIndex + 1)
        }

        return indices
    }
}
