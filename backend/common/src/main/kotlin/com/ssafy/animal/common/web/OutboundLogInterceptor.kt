package com.ssafy.animal.common.web

import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import java.io.IOException

/**
 * 이 서버가 외부(Kakao·Naver·breed-server·OpenAI 등)를 부를 때 한 줄: 도착지(destination)와 결과 (S15P31D106-423).
 * 출발지(서비스명·Pod명)는 모든 로그 줄에 붙는다(common.yml). 쿼리·헤더·본문은 주소·토큰이 담기므로 남기지 않는다.
 * RestClient.Builder에 `.requestInterceptor(OutboundLogInterceptor())`로 붙인다.
 */
class OutboundLogInterceptor : ClientHttpRequestInterceptor {

    private val log = LoggerFactory.getLogger("http.outbound")

    override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {
        val start = System.nanoTime()
        var status: Int? = null
        var error: String? = null
        try {
            return execution.execute(request, body).also { status = it.statusCode.value() }
        } catch (e: IOException) {
            error = e.javaClass.simpleName
            throw e
        } finally {
            write(request, status, error, (System.nanoTime() - start) / 1_000_000)
        }
    }

    private fun write(request: HttpRequest, status: Int?, error: String?, durationMs: Long) {
        val uri = request.uri
        val port = uri.port.takeIf { it != -1 } ?: if (uri.scheme == "https") 443 else 80
        val destination = mapOf(
            "host" to uri.host,
            "port" to port,
            "protocol" to uri.scheme,
            "method" to request.method.name(),
            "path" to uri.rawPath,
        )
        val failed = error != null || (status ?: 0) >= 500
        log.atLevel(if (failed) Level.WARN else Level.INFO)
            .addKeyValue("destination", destination)
            .addKeyValue("status", status)
            .addKeyValue("durationMs", durationMs)
            .addKeyValue("error", error)
            .log("-> {} {}://{}{} {} {}ms", request.method.name(), uri.scheme, uri.host, uri.rawPath, status ?: error, durationMs)
    }
}
