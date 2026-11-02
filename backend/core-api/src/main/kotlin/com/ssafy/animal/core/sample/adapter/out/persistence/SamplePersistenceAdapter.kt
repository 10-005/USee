package com.ssafy.animal.core.sample.adapter.out.persistence

import com.querydsl.jpa.impl.JPAQueryFactory
import com.ssafy.animal.core.sample.application.port.out.SampleRepository
import com.ssafy.animal.core.sample.domain.QSample.Companion.sample
import com.ssafy.animal.core.sample.domain.Sample
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

/** 단순 CRUD는 JpaRepository에 위임하고, 동적·복잡 조회는 QueryDSL로 작성한다. */
@Repository
class SamplePersistenceAdapter(
    private val sampleJpaRepository: SampleJpaRepository,
    private val queryFactory: JPAQueryFactory,
) : SampleRepository {

    override fun save(sample: Sample): Sample = sampleJpaRepository.save(sample)

    override fun findById(sampleSeq: Long): Sample? = sampleJpaRepository.findByIdOrNull(sampleSeq)

    override fun findPage(pageable: Pageable): Page<Sample> {
        val content = queryFactory.selectFrom(sample)
            .orderBy(sample.sampleSeq.desc())
            .offset(pageable.offset)
            .limit(pageable.pageSize.toLong())
            .fetch()
        val total = queryFactory.select(sample.count()).from(sample).fetchOne() ?: 0L
        return PageImpl(content, pageable, total)
    }

    override fun delete(sample: Sample) = sampleJpaRepository.delete(sample)
}
