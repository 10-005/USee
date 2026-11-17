package com.ssafy.animal.core.image.adapter.out.persistence

import com.ssafy.animal.core.image.domain.Image
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ImageJpaRepository : JpaRepository<Image, Long> {

    /**
     * 이미지 참조 확인(Q383)은 여기 한 곳에서 한다. DB 명세 14번의 참조 대상 전부:
     * 직접 FK(회원·보호소 프로필/배경, 댓글 2종·채팅 image_seq)와 연결 테이블 4개.
     * 품종 대표 이미지는 시드 마스터라 해제 경로가 없어 제외(Q482). 탈퇴·삭제 행도 FK가 남으므로 is_deleted로 거르지 않는다.
     * 연결 테이블·댓글·채팅 쪽은 image_seq 인덱스가 있다(V3).
     */
    @Query(
        nativeQuery = true,
        value = """
        select exists(select 1 from users where profile_image_seq = :imageSeq)
            or exists(select 1 from shelters where profile_image_seq = :imageSeq or background_image_seq = :imageSeq)
            or exists(select 1 from pet_images where image_seq = :imageSeq)
            or exists(select 1 from missing_report_images where image_seq = :imageSeq)
            or exists(select 1 from found_report_images where image_seq = :imageSeq)
            or exists(select 1 from shelter_animal_images where image_seq = :imageSeq)
            or exists(select 1 from missing_report_comments where image_seq = :imageSeq)
            or exists(select 1 from found_report_comments where image_seq = :imageSeq)
            or exists(select 1 from chat_messages where image_seq = :imageSeq)
        """,
    )
    fun isReferenced(@Param("imageSeq") imageSeq: Long): Boolean
}
