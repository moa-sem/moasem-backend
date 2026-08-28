package com.moasem.backend.domain.spending.repository

import com.moasem.backend.domain.spending.entity.EvidenceType
import com.moasem.backend.domain.spending.entity.Spending
import com.moasem.backend.domain.spending.entity.SpendingStatus
import com.moasem.backend.domain.spending.entity.SpendingTag
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SpendingRepositoryTest @Autowired constructor(
    private val spendingRepository: SpendingRepository,
    private val entityManager: EntityManager,
) {

    @Test
    @DisplayName("지출을 저장하면 enum과 증빙 정보가 그대로 복원된다")
    fun roundTrip() {
        val saved = spendingRepository.saveAndFlush(spending(tag = SpendingTag.OTHER, otherDetail = "축하 화환"))
        entityManager.clear()

        val found = spendingRepository.findById(saved.id!!).orElseThrow()

        assertThat(found.tag).isEqualTo(SpendingTag.OTHER)
        assertThat(found.otherDetail).isEqualTo("축하 화환")
        assertThat(found.evidenceType).isEqualTo(EvidenceType.RECEIPT)
        assertThat(found.evidenceMimeType).isEqualTo("image/jpeg")
        assertThat(found.evidenceFileSize).isEqualTo(204_800L)
        assertThat(found.spentOn).isEqualTo(LocalDate.of(2026, 8, 20))
        assertThat(found.status).isEqualTo(SpendingStatus.PENDING)
        assertThat(found.createdAt).isNotNull()
    }

    @Test
    @DisplayName("처리 결과도 그대로 복원된다")
    fun processedRoundTrip() {
        val spending = spending().also { it.reject(OWNER_ID, "증빙이 흐립니다.") }
        val saved = spendingRepository.saveAndFlush(spending)
        entityManager.clear()

        val found = spendingRepository.findById(saved.id!!).orElseThrow()

        assertThat(found.status).isEqualTo(SpendingStatus.REJECTED)
        assertThat(found.processedByUserId).isEqualTo(OWNER_ID)
        assertThat(found.rejectionReason).isEqualTo("증빙이 흐립니다.")
        assertThat(found.processedAt).isNotNull()
    }

    @Test
    @DisplayName("다른 행사에 속한 지출은 조회되지 않는다")
    fun findByIdAndEventIdIsolatesEvents() {
        val saved = spendingRepository.saveAndFlush(spending())

        assertThat(spendingRepository.findByIdAndEventId(saved.id!!, EVENT_ID)).isNotNull
        assertThat(spendingRepository.findByIdAndEventId(saved.id!!, OTHER_EVENT_ID)).isNull()
    }

    @Test
    @DisplayName("락 조회도 행사 경계를 지킨다")
    fun findWithLockIsolatesEvents() {
        val saved = spendingRepository.saveAndFlush(spending())

        assertThat(spendingRepository.findWithLockByIdAndEventId(saved.id!!, EVENT_ID)).isNotNull
        assertThat(spendingRepository.findWithLockByIdAndEventId(saved.id!!, OTHER_EVENT_ID)).isNull()
    }

    @Test
    @DisplayName("행사별로 상태를 걸러 페이지 단위로 조회한다")
    fun findAllByEventIdAndStatus() {
        spendingRepository.saveAndFlush(spending())
        spendingRepository.saveAndFlush(spending().also { it.approve(OWNER_ID) })
        spendingRepository.saveAndFlush(spending(eventId = OTHER_EVENT_ID))
        entityManager.clear()

        val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
        val all = spendingRepository.findAllByEventId(EVENT_ID, pageable)
        val approved = spendingRepository.findAllByEventIdAndStatus(EVENT_ID, SpendingStatus.APPROVED, pageable)

        assertThat(all.totalElements).isEqualTo(2)
        assertThat(approved.totalElements).isEqualTo(1)
        assertThat(approved.content.single().status).isEqualTo(SpendingStatus.APPROVED)
    }

    @Test
    @DisplayName("페이지 크기를 넘는 지출은 다음 페이지로 넘어간다")
    fun paginates() {
        repeat(3) { spendingRepository.saveAndFlush(spending()) }
        entityManager.clear()

        val firstPage = spendingRepository.findAllByEventId(EVENT_ID, PageRequest.of(0, 2))

        assertThat(firstPage.content).hasSize(2)
        assertThat(firstPage.totalElements).isEqualTo(3)
        assertThat(firstPage.hasNext()).isTrue()
    }

    private fun spending(
        eventId: Long = EVENT_ID,
        tag: SpendingTag = SpendingTag.MEAL,
        otherDetail: String? = null,
    ): Spending = Spending.create(
        eventId = eventId,
        applicantUserId = APPLICANT_ID,
        amount = 15_000L,
        spentOn = LocalDate.of(2026, 8, 20),
        reason = "1일차 점심 식사",
        tag = tag,
        otherDetail = otherDetail,
        evidence = Spending.Evidence(
            EvidenceType.RECEIPT,
            "spendings/$eventId/$APPLICANT_ID/evidence.jpg",
            "image/jpeg",
            204_800L,
        ),
    )

    companion object {
        private const val EVENT_ID = 100L
        private const val OTHER_EVENT_ID = 200L
        private const val APPLICANT_ID = 10L
        private const val OWNER_ID = 30L
    }
}
