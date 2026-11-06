package com.ssafy.animal.core.image.adapter.out.persistence

import com.ssafy.animal.core.image.domain.Image
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ImageJpaRepository : JpaRepository<Image, Long> {

    /**
     * 이미지 참조 확인(Q383)은 여기 한 곳에서 한다. 참조 테이블이 생기면 EXISTS를 한 줄씩 더한다
     * (명세상 남은 대상: 연결 테이블 4개, 댓글·채팅 image_seq).
     * 탈퇴·삭제 행도 FK가 남아 있으므로 is_deleted로 거르지 않는다.
     */
    @Query(
        nativeQuery = true,
        value = """
        select exists(select 1 from users where profile_image_seq = :imageSeq)
            or exists(select 1 from shelters where profile_image_seq = :imageSeq or background_image_seq = :imageSeq)
        """,
    )
    fun isReferenced(@Param("imageSeq") imageSeq: Long): Boolean
}
