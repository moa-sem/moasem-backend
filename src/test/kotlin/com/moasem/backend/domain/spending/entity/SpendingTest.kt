package com.moasem.backend.domain.spending.entity

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SpendingTest {

    @Nested
    @DisplayName("지출 신청 생성")
    inner class Create {

        @Test
        fun `생성 직후에는 PENDING 상태이고 처리 정보가 비어 있다`() {
            val spending = spending()

            assertThat(spending.status).isEqualTo(SpendingStatus.PENDING)
            assertThat(spending.isPending).isTrue()
            assertThat(spending.processedByUserId).isNull()
            assertThat(spending.processedAt).isNull()
            assertThat(spending.rejectionReason).isNull()
        }

        @Test
        fun `0원 이하 금액은 거부한다`() {
            assertThatThrownBy { spending(amount = 0L) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("0원보다 커야")
        }

        @Test
        fun `기타 태그인데 상세 내용이 없으면 거부한다`() {
            assertThatThrownBy { spending(tag = SpendingTag.OTHER, otherDetail = "  ") }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("상세 내용")
        }

        @Test
        fun `기타가 아닌 태그에는 상세 내용을 남기지 않는다`() {
            val spending = spending(tag = SpendingTag.MEAL, otherDetail = "굳이 넣은 값")

            assertThat(spending.otherDetail).isNull()
        }

        @Test
        fun `JPEG PNG 외의 증빙 형식은 거부한다`() {
            assertThatThrownBy { evidence(mimeType = "application/pdf") }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("JPEG 또는 PNG")
        }

        @Test
        fun `허용 용량을 넘는 증빙 파일은 거부한다`() {
            assertThatThrownBy { evidence(fileSize = Spending.MAX_FILE_SIZE_BYTES + 1) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("파일 크기")
        }
    }

    @Nested
    @DisplayName("지출 수정")
    inner class Update {

        @Test
        fun `PENDING 상태면 내용과 증빙을 함께 갈아끼운다`() {
            val spending = spending(tag = SpendingTag.OTHER, otherDetail = "축하 화환")

            spending.update(
                amount = 20_000L,
                spentOn = LocalDate.of(2026, 8, 21),
                reason = "저녁 식사",
                tag = SpendingTag.MEAL,
                otherDetail = null,
                evidence = evidence(storageKey = "spendings/2/new.png", mimeType = "image/png"),
            )

            assertThat(spending.amount).isEqualTo(20_000L)
            assertThat(spending.tag).isEqualTo(SpendingTag.MEAL)
            assertThat(spending.otherDetail).isNull()
            assertThat(spending.evidenceStorageKey).isEqualTo("spendings/2/new.png")
        }

        @Test
        fun `이미 처리된 건은 수정할 수 없다`() {
            val spending = spending()
            spending.approve(PROCESSOR_ID)

            assertThatThrownBy {
                spending.update(10_000L, LocalDate.of(2026, 8, 21), "사유", SpendingTag.MEAL, null, evidence())
            }.isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("PENDING")
        }
    }

    @Nested
    @DisplayName("승인 / 반려")
    inner class Process {

        @Test
        fun `승인하면 처리자와 처리 시각이 기록된다`() {
            val spending = spending()

            spending.approve(PROCESSOR_ID)

            assertThat(spending.status).isEqualTo(SpendingStatus.APPROVED)
            assertThat(spending.processedByUserId).isEqualTo(PROCESSOR_ID)
            assertThat(spending.processedAt).isNotNull()
        }

        @Test
        fun `반려하면 사유가 함께 기록된다`() {
            val spending = spending()

            spending.reject(PROCESSOR_ID, "  증빙 이미지가 흐립니다.  ")

            assertThat(spending.status).isEqualTo(SpendingStatus.REJECTED)
            assertThat(spending.rejectionReason).isEqualTo("증빙 이미지가 흐립니다.")
            assertThat(spending.processedByUserId).isEqualTo(PROCESSOR_ID)
        }

        @Test
        fun `반려 사유가 비어 있으면 거부한다`() {
            assertThatThrownBy { spending().reject(PROCESSOR_ID, "   ") }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("반려 사유")
        }

        @Test
        fun `이미 처리된 건은 다시 처리할 수 없다`() {
            val spending = spending()
            spending.approve(PROCESSOR_ID)

            assertThatThrownBy { spending.reject(PROCESSOR_ID, "뒤늦은 반려") }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("PENDING")
        }
    }

    companion object {
        private const val EVENT_ID = 1L
        private const val APPLICANT_ID = 10L
        private const val PROCESSOR_ID = 99L

        private fun evidence(
            type: EvidenceType = EvidenceType.RECEIPT,
            storageKey: String = "spendings/1/receipt.jpg",
            mimeType: String = "image/jpeg",
            fileSize: Long? = 1_024L,
        ) = Spending.Evidence(type, storageKey, mimeType, fileSize)

        private fun spending(
            amount: Long = 15_000L,
            tag: SpendingTag = SpendingTag.MEAL,
            otherDetail: String? = null,
        ) = Spending.create(
            eventId = EVENT_ID,
            applicantUserId = APPLICANT_ID,
            amount = amount,
            spentOn = LocalDate.of(2026, 8, 20),
            reason = "점심 식사",
            tag = tag,
            otherDetail = otherDetail,
            evidence = evidence(),
        )
    }
}
