package com.moasem.backend.domain.spending.entity

/** 지출 증빙 종류. 어느 쪽이든 이미지 파일 한 장을 첨부한다. */
enum class EvidenceType {
    RECEIPT,
    BANK_TRANSFER,
}
