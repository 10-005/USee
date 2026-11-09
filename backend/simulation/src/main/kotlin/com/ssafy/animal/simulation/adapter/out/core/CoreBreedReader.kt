package com.ssafy.animal.simulation.adapter.out.core

import com.ssafy.animal.simulation.application.port.out.BreedInfo
import com.ssafy.animal.simulation.application.port.out.BreedReader
import com.ssafy.animal.simulation.application.port.out.CatalogSpeciesCode
import com.ssafy.animal.simulation.domain.Personality
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component

/** 이미지 공개 URL = {publicBaseUrl}/{imageKey}. core-api의 storage.public-base-url과 같은 값을 쓴다 */
@ConfigurationProperties("storage")
data class StorageUrlProperties(
    val publicBaseUrl: String = "",
) {
    fun publicUrl(key: String) = "${publicBaseUrl.trimEnd('/')}/$key"
}

/**
 * core 스키마의 품종·종·이미지를 읽기 전용 JOIN으로 조회한다(Q420 D·Q482).
 * 소유하지 않는 테이블이라 엔티티로 매핑하지 않고(ddl validate 대상에서도 제외) 잠그지도 않는다.
 * 접속 currentSchema가 simulation이므로 core 테이블은 스키마를 붙여 쓴다.
 */
@Component
class CoreBreedReader(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val storage: StorageUrlProperties,
) : BreedReader {

    override fun findBreed(breedSeq: Int): BreedInfo? =
        jdbcTemplate.query(FIND_BREED, mapOf("breedSeq" to breedSeq)) { rs, _ ->
            BreedInfo(
                breedSeq = rs.getInt("breed_seq"),
                breedName = rs.getString("breed_name"),
                speciesCode = CatalogSpeciesCode.valueOf(rs.getString("species_code")),
                personality = Personality.valueOf(rs.getString("personality")),
                representativeImageUrl = rs.getString("image_key")?.let(storage::publicUrl),
            )
        }.firstOrNull()

    companion object {
        private val FIND_BREED = """
            SELECT b.breed_seq, b.breed_name, s.species_code, b.personality, i.image_key
            FROM core.animal_breeds b
            JOIN core.animal_species s ON s.species_seq = b.species_seq
            LEFT JOIN core.images i ON i.image_seq = b.representative_image_seq
            WHERE b.breed_seq = :breedSeq
        """.trimIndent()
    }
}
