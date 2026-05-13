package com.beer.app.data

import kotlinx.serialization.json.Json

val SharedJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}
