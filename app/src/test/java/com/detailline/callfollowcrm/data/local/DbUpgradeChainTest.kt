package com.detailline.callfollowcrm.data.local

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🗄️ **새 폰·업데이트한 폰에서 앱이 켜지는지**를 기계가 본다. (2026-10-02 사장님 지시)
 *
 *   사장님: *"어떻게 해야 상위권보다 더 높은 평을 받을 수 있어?"*
 *   → 가장 값싸고 가장 크게 올리는 것이 **이것**이다. 실제 사고가 있었다:
 *     2026-09-17, 마이그레이션이 칸을 빼먹어 **앱이 아예 안 켜졌다.**
 *     그때 기계가 미리 볼 방법이 없었다.
 *
 *   Room 은 **올라갈 길(migration)이 하나라도 없으면** 앱을 켤 때 그냥 죽는다
 *   ("A migration from X to Y was required but not found"). 우리는 일부러
 *   `fallbackToDestructiveMigration` 을 **빼놨다** — 데이터를 조용히 지우는 것보다
 *   죽는 게 낫다고 정했기 때문이다(`AppDatabase.kt` 주석). 그래서 **길이 끊기면 그대로 사고**다.
 *
 *   이 테스트는 **오탐이 날 수 없는 것만** 본다 (글자를 세는 게 아니라 **사슬과 번호**를 본다):
 *     ① 선언한 마이그레이션이 전부 `addMigrations(...)` 에 등록돼 있나
 *     ② 등록했는데 선언이 없는 것은 없나
 *     ③ 1..version 중 **올라올 길이 없는 버전**이 있나 —
 *        단 `fallbackToDestructiveMigrationFrom(...)` 에 적어 **일부러 버린 버전**은 예외
 *     ④ DB 모양 파일(`app/schemas/.../<version>.json`)이 **지금 버전과 같나**
 *
 *   ⚠️ 여기서 더 나아가 **진짜로 DB 를 만들어 올려보는 시험**(Room MigrationTestHelper)은
 *      폰이나 에뮬레이터가 있어야 한다. 그건 `61.json` 이 생긴 **다음 버전부터** 가능하다
 *      (이전 버전들의 모양 파일이 없어서) — 다음 DB 버전을 올릴 때 같이 만든다.
 */
class DbUpgradeChainTest {

    private val dbFile: File by lazy { find("src/main/java/com/detailline/callfollowcrm/data/local/AppDatabase.kt") }
    private val src: String by lazy { dbFile.readText() }

    /** 단위 테스트의 현재 폴더가 모듈(app/)인지 저장소 뿌리인지 모를 수 있어 위로 올라가며 찾는다. */
    private fun find(rel: String): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            File(dir, rel).let { if (it.exists()) return it }
            File(dir, "app/$rel").let { if (it.exists()) return it }
            dir = dir.parentFile
        }
        throw AssertionError("못 찾음: $rel")
    }

    private val version: Int by lazy {
        Regex("""version\s*=\s*(\d+)""").find(src)?.groupValues?.get(1)?.toInt()
            ?: throw AssertionError("AppDatabase 의 version 을 못 읽었다")
    }

    /** `object : Migration(a, b)` 로 **선언된** 것. */
    private val declared: Set<Pair<Int, Int>> by lazy {
        Regex("""Migration\((\d+),\s*(\d+)\)""").findAll(src)
            .map { it.groupValues[1].toInt() to it.groupValues[2].toInt() }.toSet()
    }

    /** `addMigrations(...)` 에 **등록된** 것. 등록 안 하면 Room 은 그 길을 모른다. */
    private val registered: Set<Pair<Int, Int>> by lazy {
        val block = Regex("""addMigrations\(([\s\S]*?)\)\s*\n""").find(src)?.groupValues?.get(1)
            ?: throw AssertionError("addMigrations(...) 를 못 찾았다")
        Regex("""MIGRATION_(\d+)_(\d+)""").findAll(block)
            .map { it.groupValues[1].toInt() to it.groupValues[2].toInt() }.toSet()
    }

    /** `fallbackToDestructiveMigrationFrom(1, 2)` — **일부러 버리기로 한** 버전들. */
    private val dropped: Set<Int> by lazy {
        val arg = Regex("""fallbackToDestructiveMigrationFrom\(([^)]*)\)""").find(src)?.groupValues?.get(1)
            ?: return@lazy emptySet()
        Regex("""\d+""").findAll(arg).map { it.value.toInt() }.toSet()
    }

    @Test
    fun `선언한 마이그레이션은 전부 등록돼 있어야 한다`() {
        val missing = (declared - registered).sortedBy { it.first }
        assertTrue(
            "마이그레이션을 만들어 놓고 addMigrations 에 **등록을 안 했다** — " +
                "그 버전에서 올라오는 폰은 앱이 **아예 안 켜진다**: " +
                missing.joinToString { "MIGRATION_${it.first}_${it.second}" },
            missing.isEmpty()
        )
    }

    @Test
    fun `등록했는데 선언이 없는 마이그레이션은 없어야 한다`() {
        val ghosts = (registered - declared).sortedBy { it.first }
        assertTrue(
            "addMigrations 에 적혀 있는데 선언이 없다(이름만 남음): " +
                ghosts.joinToString { "MIGRATION_${it.first}_${it.second}" },
            ghosts.isEmpty()
        )
    }

    @Test
    fun `올라올 길이 끊긴 버전이 없어야 한다`() {
        val from = registered.map { it.first }.toSet()
        val gaps = (1 until version).filter { it !in from && it !in dropped }
        assertTrue(
            "이 버전을 쓰는 폰은 **올라갈 길이 없어 앱이 안 켜진다** (버려도 되는 버전이면 " +
                "fallbackToDestructiveMigrationFrom 에 적어야 한다): $gaps",
            gaps.isEmpty()
        )
    }

    @Test
    fun `마이그레이션은 한 단씩 이어져야 한다`() {
        val bad = registered.filter { it.second != it.first + 1 }
        assertTrue("한 단씩(n → n+1) 이 아니다: $bad", bad.isEmpty())
    }

    @Test
    fun `DB 모양 파일이 지금 버전과 같아야 한다`() {
        val dir = find("schemas/com.detailline.callfollowcrm.data.local.AppDatabase")
        val f = File(dir, "$version.json")
        assertTrue(
            "DB 모양 파일이 없다: ${f.name}. 버전을 올렸으면 빌드해서 " +
                "app/schemas 에 생긴 파일을 **git 에 같이 올려야** 한다 " +
                "(있는 것: ${dir.list()?.sorted()})",
            f.exists()
        )
        val inJson = Regex(""""version"\s*:\s*(\d+)""").find(f.readText())?.groupValues?.get(1)?.toInt()
        assertEquals("모양 파일 안의 버전이 다르다", version, inJson)
    }

    @Test
    fun `마지막 마이그레이션이 지금 버전까지 와 있어야 한다`() {
        val top = registered.maxOfOrNull { it.second } ?: 0
        assertEquals(
            "version 은 올렸는데 거기까지 올라가는 마이그레이션이 없다 — 업데이트한 폰이 안 켜진다",
            version, top
        )
    }
}
