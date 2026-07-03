package com.detailline.callfollowcrm.util

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 사업자등록증 자동 입력 — 온디바이스(무료) 한국어 OCR + 필드 파싱. (2026-07-04 사장님)
 *   ML Kit Korean text recognition = 서버·API키 불필요, 오프라인. 유료 STT 금지 원칙과 별개(로컬 무료).
 *   결과는 항상 사용자가 확인·수정 가능한 값으로 채우기만 함(덮어쓰기 강요 X).
 */
object BizCertOcr {

    data class Fields(
        val bizNo: String? = null,   // 숫자만 (예: "1234567890")
        val name: String? = null,    // 상호(법인명)
        val owner: String? = null,   // 대표자
        val addr: String? = null     // 사업장 소재지
    ) {
        val any: Boolean get() = bizNo != null || name != null || owner != null || addr != null
    }

    /** 이미지 URI → 인식된 전체 텍스트. 실패/못 읽으면 null. */
    suspend fun recognize(context: Context, uri: Uri): String? =
        suspendCancellableCoroutine { cont ->
            runCatching {
                val image = InputImage.fromFilePath(context, uri)
                val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
                recognizer.process(image)
                    .addOnSuccessListener { cont.resume(it.text.takeIf { t -> t.isNotBlank() }) }
                    .addOnFailureListener { cont.resume(null) }
            }.onFailure { cont.resume(null) }
        }

    private val BIZ_NO = Regex("""\d{3}-\d{2}-\d{5}""")
    // 주소로 보이는 다음 줄인지(라벨류 아님 + 주소 토큰 있음).
    private val ADDR_TOKEN = Regex("""(특별시|광역시|특별자치|[가-힣]+시|[가-힣]+군|[가-힣]+구|[가-힣]+동|[가-힣]+읍|[가-힣]+면|[가-힣]+리|로|길|번지|층|호)""")
    private val OTHER_LABEL = Regex("""(대표자|성\s*명|개업|업태|종목|사업자|등록번호|생년|법인등록|교부)""")

    /** 인식 텍스트에서 사업자번호·상호·대표자·주소 추출. 못 찾은 항목은 null. */
    fun parse(raw: String): Fields {
        val text = raw.replace('：', ':')                 // 전각 콜론 정규화
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }

        val bizNo = BIZ_NO.find(text)?.value?.filter { it.isDigit() }

        // 라벨은 공백 무시로 매칭 — 등록증은 "상 호", "성 명"처럼 띄어 인쇄돼 OCR 에 공백이 낌. (2026-07-04 사장님)
        //   값은 원본에서 ':' 뒤를 취하고, 다른 필드 라벨이 섞여 오면 그 앞까지만.
        fun valueAfter(vararg labels: String): String? {
            val wanted = labels.map { it.replace(" ", "") }
            for (i in lines.indices) {
                val l = lines[i]
                val compact = l.replace(" ", "")
                if (wanted.none { compact.contains(it) }) continue
                var rest = if (l.contains(":")) {
                    l.substringAfter(":").trim()
                } else {
                    // ':' 없으면 라벨(글자 사이 공백 허용)을 원본에서 제거 → 값의 공백은 보존.
                    var r = l
                    labels.forEach { lb ->
                        val pat = lb.filter { it != ' ' }.map { Regex.escape(it.toString()) }.joinToString("\\s*")
                        r = r.replace(Regex(pat), "")
                    }
                    r.trim(' ', '(', ')', ':', '·', '.', '/', '*', '①', '②', '③', '④', '⑤', '⑥')
                }
                // 라벨만 있고 값은 다음 줄인 경우(표 형식).
                if (rest.isBlank() || rest.length < 2) rest = lines.getOrNull(i + 1)?.trim().orEmpty()
                // 값에 다른 필드 라벨이 붙어오면 그 앞까지만(같은 줄에 여러 칸).
                rest = rest.split(Regex("성\\s*명|대\\s*표|개\\s*업|사\\s*업\\s*장|소\\s*재|업\\s*태|종\\s*목|전\\s*화|생\\s*년"))[0].trim()
                // 사업자번호가 섞여 오면 제거.
                rest = BIZ_NO.replace(rest, "").trim(' ', ':', '·', '(', ')', '.')
                if (rest.isNotBlank()) return rest
            }
            return null
        }

        val name = valueAfter("상호(법인명)", "상호", "법인명", "단체명")
        val owner = valueAfter("성명(대표자)", "대표자", "성명", "대표")?.let { o ->
            // 대표자 뒤에 생년월일 등이 붙으면 첫 토큰(이름)만.
            o.split(Regex("\\s{2,}|\\(|\\d")).firstOrNull()?.trim()?.takeIf { it.isNotBlank() } ?: o
        }

        // 주소 — 라벨 줄 값 + 다음 줄이 주소로 이어지면 붙임.
        var addr: String? = null
        for (i in lines.indices) {
            val l = lines[i]
            val c = l.replace(" ", "")
            if (c.contains("소재지") || c.contains("사업장") || c.contains("소재")) {
                var a = if (l.contains(":")) l.substringAfter(":").trim() else l.replace("사업장 소재지", "").replace("소재지", "").replace("사업장", "").trim(' ', ':', '·')
                if (a.isBlank() && i + 1 < lines.size) a = lines[i + 1].trim()
                // 다음 줄이 주소 연장이면 붙임(라벨류 아님 + 주소 토큰 있음).
                val next = lines.getOrNull(i + if (a == lines.getOrNull(i + 1)?.trim()) 2 else 1)
                if (!next.isNullOrBlank() && !OTHER_LABEL.containsMatchIn(next) && ADDR_TOKEN.containsMatchIn(next)) {
                    a = "$a ${next.trim()}".trim()
                }
                if (a.isNotBlank()) { addr = a; break }
            }
        }

        return Fields(bizNo = bizNo, name = name, owner = owner, addr = addr)
    }
}
