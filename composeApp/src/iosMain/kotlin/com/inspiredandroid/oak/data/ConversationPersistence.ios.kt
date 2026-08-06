package com.inspiredandroid.oak.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.inspiredandroid.oak.db.OakDatabase

actual fun createConversationSqlDriver(): SqlDriver? = NativeSqliteDriver(OakDatabase.Schema, "conversations.db")
