package com.detailline.callfollowcrm.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "call_records",
    indices = [Index("phoneNumber"), Index("endedAt"), Index("linkedCustomerId")]
)
data class CallRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNumber: String,
    val callType: String,
    val duration: Long,
    val startedAt: Long? = null,
    val endedAt: Long,
    val handledStatus: String,
    val linkedCustomerId: Long? = null
)
