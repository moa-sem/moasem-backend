package com.moasem.backend.global.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.hibernate.type.format.AbstractJsonFormatMapper
import java.lang.reflect.Type

/**
 * JSONB 컬럼 매핑에 Kotlin·JavaTime 지원을 더한 Hibernate [org.hibernate.type.format.FormatMapper].
 *
 * Hibernate는 JSON 매핑에 Jackson 2(`com.fasterxml.jackson`)를 사용하는데, 기본 ObjectMapper에는
 * Kotlin 모듈이 없어 기본 생성자가 없는 `data class`를 역직렬화하지 못한다.
 * Spring 본체가 쓰는 Jackson 3(`tools.jackson`)과는 별개의 인스턴스다.
 *
 * `application.yml`의 `hibernate.type.json_format_mapper`로 등록한다.
 */
class KotlinJsonFormatMapper : AbstractJsonFormatMapper() {

    override fun <T : Any?> fromString(charSequence: CharSequence, type: Type): T =
        objectMapper.readValue(charSequence.toString(), objectMapper.constructType(type))

    override fun <T : Any?> toString(value: T, type: Type): String =
        objectMapper.writerFor(objectMapper.constructType(type)).writeValueAsString(value)

    companion object {
        private val objectMapper: ObjectMapper = ObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .registerModule(JavaTimeModule())
            // 날짜를 epoch 숫자가 아니라 ISO-8601 문자열로 저장해 사람이 읽을 수 있게 한다.
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }
}
