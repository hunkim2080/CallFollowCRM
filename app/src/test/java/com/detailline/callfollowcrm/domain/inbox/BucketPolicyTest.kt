package com.detailline.callfollowcrm.domain.inbox

import com.detailline.callfollowcrm.data.local.entity.ThreadBucketEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BucketPolicyTest {

    private fun row(bucket: String, source: String, hash: Int? = 1) =
        ThreadBucketEntity("12345678", bucket, source, null, 1000L, hash)

    private fun decide(existing: ThreadBucketEntity?, bucket: String, source: String, hash: Int? = 1) =
        BucketPolicy.decide(existing, "12345678", bucket, source, null, hash, 2000L)

    @Test fun `행 없을 때 GENERAL 판정은 새로 쓴다`() {
        val a = decide(null, BucketPolicy.GENERAL, BucketPolicy.SRC_LOCAL)
        assertTrue(a is BucketPolicy.Action.Write)
        assertEquals(BucketPolicy.GENERAL, (a as BucketPolicy.Action.Write).entity.bucket)
    }

    @Test fun `행 없을 때 자동 CONSULT 는 아무것도 안 함`() {
        assertEquals(BucketPolicy.Action.Noop, decide(null, BucketPolicy.CONSULT, BucketPolicy.SRC_LOCAL))
    }

    @Test fun `기존 GENERAL 에 자동 CONSULT 승격은 행 삭제(상담함 복귀)`() {
        val a = decide(row(BucketPolicy.GENERAL, BucketPolicy.SRC_LOCAL), BucketPolicy.CONSULT, BucketPolicy.SRC_LOCAL)
        assertEquals(BucketPolicy.Action.Delete, a)
    }

    @Test fun `OWNER 상담함 결정은 자동 GENERAL 이 못 덮는다`() {
        val a = decide(row(BucketPolicy.CONSULT, BucketPolicy.SRC_OWNER), BucketPolicy.GENERAL, BucketPolicy.SRC_LOCAL)
        assertEquals(BucketPolicy.Action.Noop, a)
    }

    @Test fun `OWNER 문자함 결정은 자동 CONSULT 가 못 덮는다`() {
        val a = decide(row(BucketPolicy.GENERAL, BucketPolicy.SRC_OWNER), BucketPolicy.CONSULT, BucketPolicy.SRC_LOCAL)
        assertEquals(BucketPolicy.Action.Noop, a)
    }

    @Test fun `같은 내용 GENERAL 재분류는 noop(재호출 방지)`() {
        val a = decide(row(BucketPolicy.GENERAL, BucketPolicy.SRC_LOCAL, hash = 42), BucketPolicy.GENERAL, BucketPolicy.SRC_LOCAL, hash = 42)
        assertEquals(BucketPolicy.Action.Noop, a)
    }

    @Test fun `HAIKU 는 LOCAL GENERAL 을 덮을 수 있다`() {
        val a = decide(row(BucketPolicy.GENERAL, BucketPolicy.SRC_LOCAL, hash = 42), BucketPolicy.GENERAL, BucketPolicy.SRC_HAIKU, hash = 42)
        assertTrue(a is BucketPolicy.Action.Write)
        assertEquals(BucketPolicy.SRC_HAIKU, (a as BucketPolicy.Action.Write).entity.source)
    }

    @Test fun `OWNER 는 OWNER 를 덮을 수 있다(재이동)`() {
        val a = decide(row(BucketPolicy.GENERAL, BucketPolicy.SRC_OWNER), BucketPolicy.CONSULT, BucketPolicy.SRC_OWNER)
        assertTrue(a is BucketPolicy.Action.Write)
        assertEquals(BucketPolicy.CONSULT, (a as BucketPolicy.Action.Write).entity.bucket)
    }
}
