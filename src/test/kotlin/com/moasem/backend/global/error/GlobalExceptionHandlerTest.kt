package com.moasem.backend.global.error

import com.moasem.backend.global.response.ApiResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import com.moasem.backend.global.security.JwtAuthenticationFilter
import com.ninjasquad.springmockk.MockkBean
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.http.MediaType
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 예외가 실제 HTTP 응답으로 어떻게 변환되는지 확인한다.
 *
 * 상태 코드와 JSON 본문을 함께 검증해야 의미가 있어 MockMvc로 end-to-end로 확인한다.
 */
@WebMvcTest(controllers = [TestController::class])
@Import(GlobalExceptionHandler::class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var jwtAuthenticationFilter: JwtAuthenticationFilter

    @Test
    @DisplayName("성공 응답에 success, code, message가 담긴다")
    fun successResponse() {
        mockMvc.perform(get("/test/success"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.message").value("요청이 성공했습니다."))
            .andExpect(jsonPath("$.data.name").value("모아셈"))
            .andExpect(jsonPath("$.timestamp").exists())
    }

    @Test
    @DisplayName("성공 응답에는 errors 필드가 생략된다")
    fun omitsNullFields() {
        mockMvc.perform(get("/test/success"))
            .andExpect(jsonPath("$.errors").doesNotExist())
    }

    @Test
    @DisplayName("BusinessException은 ErrorCode의 상태 코드로 변환된다")
    fun businessException() {
        mockMvc.perform(get("/test/business"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("NOT_GROUP_MEMBER"))
            .andExpect(jsonPath("$.message").value("모임 구성원이 아닙니다."))
            .andExpect(jsonPath("$.data").doesNotExist())
    }

    @Test
    @DisplayName("검증 실패 시 필드 단위 오류가 담긴다")
    fun validationError() {
        mockMvc.perform(
            post("/test/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"","amount":-1}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
            .andExpect(jsonPath("$.errors").isArray)
            .andExpect(jsonPath("$.errors.length()").value(2))
            .andExpect(jsonPath("$.errors[?(@.field == 'title')]").exists())
    }

    @Test
    @DisplayName("파라미터 타입이 맞지 않으면 400으로 변환된다")
    fun typeMismatch() {
        mockMvc.perform(get("/test/param").param("count", "숫자아님"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_TYPE_VALUE"))
    }

    @Test
    @DisplayName("필수 파라미터가 없으면 400으로 변환된다")
    fun missingParameter() {
        mockMvc.perform(get("/test/param"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("MISSING_REQUEST_PARAMETER"))
    }

    @Test
    @DisplayName("처리하지 않은 예외는 500으로 변환되고 원인이 노출되지 않는다")
    fun unexpectedException() {
        mockMvc.perform(get("/test/boom"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
            .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("DB 커넥션"))))
    }

    @Test
    @DisplayName("NoSuchElementException은 404로 변환된다")
    fun noSuchElement() {
        mockMvc.perform(get("/test/missing"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("ENTITY_NOT_FOUND"))
    }
}

@RestController
class TestController {

    @GetMapping("/test/success")
    fun success(): ApiResponse<Map<String, String>> = ApiResponse.success(mapOf("name" to "모아셈"))

    @GetMapping("/test/business")
    fun business(): Nothing = throw BusinessException(ErrorCode.NOT_GROUP_MEMBER)

    @GetMapping("/test/missing")
    fun missing(): Nothing = throw NoSuchElementException("보고서를 찾을 수 없습니다.")

    @GetMapping("/test/boom")
    fun boom(): Nothing = throw RuntimeException("DB 커넥션 실패: jdbc:postgresql://internal-host:5432")

    @GetMapping("/test/param")
    fun param(@RequestParam count: Int): ApiResponse<Int> = ApiResponse.success(count)

    @PostMapping("/test/validate")
    fun validate(@Valid @RequestBody request: TestRequest): ApiResponse<Unit> = ApiResponse.ok()
}

data class TestRequest(
    @field:NotBlank(message = "제목은 비어 있을 수 없습니다.")
    val title: String,

    @field:Min(value = 0, message = "금액은 0원 이상이어야 합니다.")
    val amount: Long,
)
