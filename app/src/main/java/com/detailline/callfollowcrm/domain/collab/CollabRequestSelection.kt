package com.detailline.callfollowcrm.domain.collab

/**
 * 🤝 **「같이 할 사장님」 시트에서 — 누가 골라졌고, 무엇이 취소인가.**
 *
 * 이 셈이 틀리면 **사장님이 보낸 협업이 조용히 취소된다.** 실제로 두 번 그랬다:
 *
 *   · 2026-09-27 — 협업 자료가 **시트가 그려진 뒤에** 와서, 이미 요청한 사장님이
 *     안 골라진 채로 남았다. 그대로 [보내기] 를 누르면 앱이 그걸 **「그 사람을 뺐다」**로 읽고
 *     「보낸 협업을 취소할까요?」를 띄웠다. (사장님 *"더 추가가안되네"*)
 *   · 2026-09-27 — 명부에 **줄이 없는** 번호로 보낸 요청이 있으면 사장님은 그 줄을
 *     **누를 수가 없는데도** 「안 골랐다」로 세어져 **영영 빼는 중**이 됐다.
 *
 * ⚠️ **폰에서는 이걸 눈으로 못 본다** — 진짜 협업 요청을 쏴야 재현되고, 그건 상대 사장님께
 *    실제로 문자·알림이 가는 일이다. 그래서 **셈을 여기 떼어 시험이 대신 본다** (CLAUDE.md §12-E②).
 *
 * 들어오는 값은 전부 **번호 끝 8자리 키**(`PhoneKey.of`)다 — 번호 모양이 달라도 같은 사람이게.
 */
object CollabRequestSelection {

    /**
     * 취소할 **수 있는** 요청 = 이미 보낸 요청 중 **명부에 줄이 있는 것만.**
     * 줄이 없으면 사장님이 누를 수 없으니, 안 골랐다고 해서 뺀 것이 아니다.
     */
    fun cancelable(requested: Set<String>, listed: Set<String>): Set<String> =
        requested.filter { it in listed }.toSet()

    /**
     * [보내기] 를 누를 때 **「보낸 협업을 취소할까요?」를 띄워야 하나.**
     * 누를 수 있었던 요청 중 **골라지지 않은 게 하나라도** 있으면 = 뺀 것이다.
     */
    fun isCancelling(requested: Set<String>, listed: Set<String>, selected: Set<String>): Boolean =
        cancelable(requested, listed).any { it !in selected }

    /**
     * 늦게 온 요청 목록을 **한 번만** 골라진 상태로 채운다.
     *
     * ⚠️ **한 번만** 이다. 매번 채우면 사장님이 **일부러 뺀 사람이 도로 들어와** 취소를 할 수가 없다.
     * @return (채운 뒤의 고른 목록, 이제 채웠는지)
     */
    fun seed(
        selected: Set<String>,
        requested: Set<String>,
        alreadySeeded: Boolean
    ): Pair<Set<String>, Boolean> = when {
        alreadySeeded -> selected to true
        requested.isEmpty() -> selected to false   // 아직 자료가 안 왔다 — 다음에 다시 본다
        else -> (selected + requested) to true
    }
}
