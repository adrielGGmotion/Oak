package com.inspiredandroid.oak.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.inspiredandroid.oak.db.OakDatabase
import com.inspiredandroid.oak.getAppFilesDirectory
import java.io.File

actual fun createConversationSqlDriver(): SqlDriver? {
    val file = File(getAppFilesDirectory(), "conversations.db")
    file.parentFile?.mkdirs()
    return JdbcSqliteDriver(url = "jdbc:sqlite:${file.absolutePath}", schema = OakDatabase.Schema)
}
