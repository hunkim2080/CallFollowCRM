package com.detailline.callfollowcrm.service

import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.detailline.callfollowcrm.util.PhoneKey

/**
 * 🔔 같은 사람한테 **여러 통** 오면 알림에 **쌓아서** 보여준다(카톡식 "N개의 메시지"). (2026-10-22 사장님)
 *   전엔 번호별 알림 1개가 **마지막 1통만** 덮어써 보여 앞 내용이 사라졌다.
 *
 * - 번호별로 최근 몇 통을 메모리에 들고 있다가(프로세스 죽으면 비어도 됨 — 알림은 휘발), 2통 이상이면 MessagingStyle 로 쌓아 보임.
 * - 대화를 열어 읽으면([NotificationHelper.cancelIncomingSms]) 그 번호 버퍼를 비운다 → 다음 묶음은 1부터.
 * - 키는 수신 알림 id([NotificationHelper.smsNotificationId])와 같은 [PhoneKey.of].
 */
object SmsNotificationStyle {

    private const val CAP = 6
    private val recent = java.util.concurrent.ConcurrentHashMap<String, ArrayDeque<Pair<Long, String>>>()

    private fun keyOf(phone: String): String? {
        val digits = phone.filter { it.isDigit() }
        return if (digits.length >= 4) PhoneKey.of(digits) else null
    }

    /** 한 통 받을 때마다 버퍼에 쌓는다(번호별, 최근 CAP개). */
    fun record(phone: String, timeMs: Long, body: String) {
        val key = keyOf(phone) ?: return
        val dq = recent.getOrPut(key) { ArrayDeque() }
        synchronized(dq) {
            dq.addLast(timeMs to body)
            while (dq.size > CAP) dq.removeFirst()
        }
    }

    /** 대화를 읽으면 그 번호 쌓인 것 비우기. */
    fun clear(phone: String) {
        keyOf(phone)?.let { recent.remove(it) }
    }

    /** 지금 쌓인 통수(뱃지 'N'). */
    fun count(phone: String): Int =
        keyOf(phone)?.let { key -> recent[key]?.let { dq -> synchronized(dq) { dq.size } } } ?: 0

    /** **2통 이상**일 때만 MessagingStyle(누적) 반환. 1통이면 null → 기존 BigText 유지. */
    fun styleOrNull(phone: String, selfName: String, otherName: String): NotificationCompat.MessagingStyle? {
        val key = keyOf(phone) ?: return null
        val dq = recent[key] ?: return null
        val items = synchronized(dq) { dq.toList() }
        if (items.size < 2) return null
        val them = Person.Builder().setName(otherName).build()
        val style = NotificationCompat.MessagingStyle(Person.Builder().setName(selfName).build())
        items.forEach { (t, b) -> style.addMessage(b, t, them) }
        return style
    }
}
