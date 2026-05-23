package com.oak.app.data

data class StorageVolume(
    val id: String,
    val label: String,
    val path: String,
    val isPrimary: Boolean,
    val isRemovable: Boolean,
)
