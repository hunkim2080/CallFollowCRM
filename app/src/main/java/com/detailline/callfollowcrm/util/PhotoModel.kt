package com.detailline.callfollowcrm.util

import android.content.Context
import coil.request.ImageRequest
import java.io.File

/**
 * 🖼️ **사진을 화면에 띄울 때 쓰는 열쇠 — 여기 한 곳.** (2026-10-02 사장님)
 *
 *   사장님: "현장사진에서 회전을 돌리고 ✕를 눌렀어. 그러면 **현장사진 탭에서도 회전한 게 보여야** 하는데
 *            그렇게 안 보이고, 클릭해서 들어가면 회전되어 있어."
 *
 * ## 왜 그랬나
 * 돌리기는 **같은 이름의 파일을 덮어쓴다**(경로가 안 바뀐다).
 * 그런데 그림을 그리는 쪽은 **「파일 이름이 같으면 같은 그림」**이라고 믿고
 * 먼저 읽어둔 걸 그대로 쓴다 — 그래서 **작은 사진은 옛날 그림**이 남는다.
 * 큰 사진은 다시 읽는 길이라 돌아가 보였고, 그래서 **두 그림이 서로 달랐다.**
 *
 * ## 고친 방법
 * 열쇠에 **파일이 바뀐 시각**을 붙인다. 돌리면 시각이 바뀌니 **다른 열쇠**가 되고,
 * 들고 있던 옛 그림을 안 쓴다. 안 바뀌었으면 열쇠가 같아 **그대로 빨리** 뜬다.
 *
 * ⚠️ 사진을 띄우는 곳은 **전부 이걸 쓴다.** 한 곳만 쓰면 그 화면만 고쳐진다(§12).
 */
object PhotoModel {

    /** 그 파일을 띄우는 요청. 파일이 바뀌면 열쇠도 바뀐다. */
    fun of(context: Context, path: String): ImageRequest {
        val file = File(path)
        val key = "$path:${file.lastModified()}:${file.length()}"
        return ImageRequest.Builder(context)
            .data(file)
            .memoryCacheKey(key)
            .diskCacheKey(key)
            .build()
    }

    /** `file://` 주소로 들고 있을 때. 같은 열쇠 규칙을 쓴다. */
    fun of(context: Context, uri: android.net.Uri): ImageRequest {
        val p = uri.path
        return if (uri.scheme == "file" && p != null) of(context, p)
        else ImageRequest.Builder(context).data(uri).build()
    }
}
