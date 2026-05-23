package com.detailline.callfollowcrm.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 문자앱 작성 화면만 연다. SEND_SMS / SmsManager 는 절대 사용하지 않는다.
 *
 * 두 가지 모드:
 *  1) 첨부 없음: ACTION_SENDTO + smsto 스킴 (수신자 번호 + 본문 prefill, chooser 없음)
 *  2) 첨부 있음: ACTION_SEND + 이미지 mime + EXTRA_STREAM + EXTRA_TEXT (chooser 통해 메시지 앱 선택)
 *     MMS 모드로 본문 + 사진 prefill. 수신자 번호 자동 입력은 앱마다 다름.
 */
object SmsIntentHelper {

    sealed interface Result {
        data object Opened : Result
        data class Failed(val reason: String) : Result
    }

    fun openSmsCompose(context: Context, phoneNumber: String, body: String): Result {
        val number = phoneNumber.trim()
        if (number.isBlank()) return Result.Failed("전화번호가 비어 있습니다.")
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$number")
            putExtra("sms_body", body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            Result.Opened
        } catch (e: ActivityNotFoundException) {
            Result.Failed("기본 문자앱을 찾을 수 없습니다.")
        } catch (e: SecurityException) {
            Result.Failed("문자앱 실행 권한이 없습니다.")
        } catch (e: Exception) {
            Result.Failed(e.message ?: "알 수 없는 오류")
        }
    }

    /**
     * 첨부 사진과 본문을 한 번에 prefill 하는 흐름.
     * Android 표준 ACTION_SEND 인텐트를 chooser 로 띄운다 — 사용자가 "메시지" 앱 선택 시
     * MMS 모드로 본문 + 사진 자동 첨부. 일부 메시지 앱(삼성)은 수신자도 자동 입력.
     */
    fun openSmsComposeWithAttachments(
        context: Context,
        phoneNumber: String,
        body: String,
        attachmentUris: List<Uri>
    ): Result {
        if (attachmentUris.isEmpty()) {
            // 첨부 없으면 단순 경로로 fallback
            return openSmsCompose(context, phoneNumber, body)
        }
        val number = phoneNumber.trim()

        // 갤럭시 OneUI 메시지 앱은 ACTION_SEND with image 인텐트를 받으면
        // 수신자 자동 입력을 무시하고 항상 수신인 선택 화면을 먼저 띄운다.
        // 그래서 번호를 클립보드에 미리 복사 — 사용자가 검색창에 한 번에 붙여넣을 수 있도록.
        if (number.isNotBlank()) {
            runCatching {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("phone", number))
            }
        }

        val sendIntent = if (attachmentUris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, attachmentUris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(attachmentUris))
            }
        }.apply {
            putExtra(Intent.EXTRA_TEXT, body)
            putExtra("sms_body", body)
            // 일부 메시지 앱이 인식하는 비공식 extras — 인식하면 수신자 자동 입력
            if (number.isNotBlank()) {
                putExtra("address", number)
                putExtra(Intent.EXTRA_PHONE_NUMBER, number)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // 일부 OEM 은 ClipData 의 URI 도 함께 읽음 — read 권한 전파를 위해 같이 세팅
            clipData = ClipData.newRawUri("attachments", attachmentUris.first()).apply {
                attachmentUris.drop(1).forEach { addItem(ClipData.Item(it)) }
            }
        }

        // 1차 시도 — 기본 SMS/메시지 앱을 강제 지정해 chooser 안 뜨게.
        // 갤럭시 폰이면 보통 com.samsung.android.messaging. 사장님이 다른 앱 기본으로 설정했으면 그 앱.
        val defaultSmsPackage = runCatching {
            android.provider.Telephony.Sms.getDefaultSmsPackage(context)
        }.getOrNull()
        if (!defaultSmsPackage.isNullOrBlank()) {
            val direct = Intent(sendIntent).apply { setPackage(defaultSmsPackage) }
            val opened = runCatching {
                context.startActivity(direct)
                true
            }.getOrDefault(false)
            if (opened) return Result.Opened
            // 기본 SMS 앱이 image/* 인텐트를 처리 못 하는 드문 경우 → chooser fallback 으로 흘러감
        }

        // 2차 fallback — 기본 SMS 앱이 없거나 위에서 실패 → chooser 표시
        val chooser = Intent.createChooser(sendIntent, "어떤 앱으로 보낼까요?").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            context.startActivity(chooser)
            Result.Opened
        } catch (e: ActivityNotFoundException) {
            Result.Failed("이미지를 받을 수 있는 앱을 찾을 수 없어요.")
        } catch (e: SecurityException) {
            Result.Failed("앱 실행 권한이 없습니다.")
        } catch (e: Exception) {
            Result.Failed(e.message ?: "알 수 없는 오류")
        }
    }
}
