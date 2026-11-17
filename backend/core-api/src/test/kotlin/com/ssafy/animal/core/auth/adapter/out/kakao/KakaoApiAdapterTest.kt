package com.ssafy.animal.core.auth.adapter.out.kakao

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.core.auth.domain.AuthErrorCode
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KakaoApiAdapterTest {

    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val adapter = KakaoApiAdapter(KakaoProperties(appId = 1639429), builder)

    private fun expectTokenInfo() = server.expect(requestTo("https://kapi.kakao.com/v1/user/access_token_info"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer kakao-token"))

    private fun assertFails(code: Any) =
        assertEquals(code, assertFailsWith<BusinessException> { adapter.fetchKakaoId("kakao-token") }.errorCode)

    @Test
    fun `우리 앱 토큰이면 카카오 회원번호를 돌려준다`() {
        expectTokenInfo().andRespond(withSuccess("""{"id":123456789,"expires_in":7199,"app_id":1639429}""", MediaType.APPLICATION_JSON))
        assertEquals("123456789", adapter.fetchKakaoId("kakao-token"))
    }

    @Test
    fun `다른 앱에서 발급된 토큰은 AUTH_INVALID_KAKAO_TOKEN`() {
        expectTokenInfo().andRespond(withSuccess("""{"id":123456789,"expires_in":7199,"app_id":999}""", MediaType.APPLICATION_JSON))
        assertFails(AuthErrorCode.AUTH_INVALID_KAKAO_TOKEN)
    }

    @Test
    fun `카카오가 401이면 AUTH_INVALID_KAKAO_TOKEN`() {
        expectTokenInfo().andRespond(
            withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body("""{"msg":"this access token does not exist","code":-401}"""),
        )
        assertFails(AuthErrorCode.AUTH_INVALID_KAKAO_TOKEN)
    }

    @Test
    fun `카카오 장애(5xx)는 COMMON_SERVICE_UNAVAILABLE`() {
        expectTokenInfo().andRespond(withStatus(HttpStatus.BAD_GATEWAY))
        assertFails(CommonErrorCode.COMMON_SERVICE_UNAVAILABLE)
    }
}
