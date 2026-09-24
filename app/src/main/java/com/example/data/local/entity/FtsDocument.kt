package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Fts4(tokenizer = "unicode61")
@Entity(tableName = "fts_documents")
data class FtsDocument(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long = 0,
    val fileUri: String,
    val pageNumber: Int = 1,
    val title: String,
    val body: String
)
