package com.ssafy.animal.core.missing.application.port.out

import com.ssafy.animal.core.missing.application.port.`in`.MissingSearchFilter
import com.ssafy.animal.core.missing.domain.MissingReport
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface MissingReportRepository {
    /** 바로 반영한다. 회원당 ACTIVE 1건 부분 UNIQUE에 걸리면 409 MISSING_ACTIVE_ALREADY_EXISTS로 바꿔 던진다 */
    fun saveAndFlush(report: MissingReport): MissingReport

    /** 비삭제·작성자 비탈퇴만(7.2) */
    fun findVisible(missingReportSeq: Int): MissingReport?

    /** 비삭제·본인 작성만(7.5~7.7) */
    fun findOwned(userSeq: Int, missingReportSeq: Int): MissingReport?

    /** 회원의 비삭제 ACTIVE 신고가 있는지 */
    fun existsActive(userSeq: Int): Boolean

    /** 7.1 비삭제·작성자 비탈퇴 + 필터. 정렬은 regDttm만 */
    fun search(filter: MissingSearchFilter, pageable: Pageable): Page<MissingReport>

    /** 7.3 비삭제 전량, regDttm desc·PK desc */
    fun findMine(userSeq: Int): List<MissingReport>

    /** display_order 순 imageSeq */
    fun findImageSeqs(missingReportSeq: Int): List<Long>

    /** missingReportSeq → 대표(display_order 1) imageSeq */
    fun findRepresentativeImageSeqs(missingReportSeqs: Collection<Int>): Map<Int, Long>

    /** 연결을 지우고 주어진 순서대로 display_order 1..N으로 다시 넣는다(빈 목록이면 지우기만). DB에 바로 반영된다 */
    fun replaceImages(missingReportSeq: Int, imageSeqs: List<Long>)

    /** 매칭 이벤트용 S3 키. 입력 순서를 지킨다 */
    fun findImageKeys(imageSeqs: List<Long>): List<String>

    // 아래는 다른 도메인 테이블(users·bookmarks·missing_report_comments)을 네이티브로 읽는다

    fun findAuthor(userSeq: Int): AuthorRow?

    fun isBookmarked(userSeq: Int, missingReportSeq: Int): Boolean

    /** 비삭제 댓글·답글 수 */
    fun countComments(missingReportSeq: Int): Long
}

interface AuthorRow {
    val userSeq: Int
    val nickname: String?
    val role: String
    val profileImageSeq: Long?
}
