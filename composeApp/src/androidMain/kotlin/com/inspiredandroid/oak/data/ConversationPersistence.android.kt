package com.inspiredandroid.oak.data

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.inspiredandroid.oak.db.OakDatabase
import org.koin.java.KoinJavaComponent.inject

actual fun createConversationSqlDriver(): SqlDriver? {
    val context: Context by inject(Context::class.java)
    return AndroidSqliteDriver(OakDatabase.Schema, context, "conversations.db")
}
