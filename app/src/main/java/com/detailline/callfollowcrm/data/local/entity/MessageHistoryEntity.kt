package com.detailline.callfollowcrm.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "message_histories",
    indices = [Index("phoneNumber"), Index("customerId"), Index("createdAt")]
)
data class MessageHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNumber: String,
    val customerId: Long? = null,
    val templateId: Long? = null,
    val messageBody: String,
    val status: String,
    val createdAt: Long
)
