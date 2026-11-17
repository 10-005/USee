package com.ssafy.animal.common.config

import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration
import org.springframework.context.annotation.Bean

@AutoConfiguration(after = [HibernateJpaAutoConfiguration::class])
@ConditionalOnBean(EntityManagerFactory::class)
class CommonJpaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun jpaQueryFactory(entityManager: EntityManager) = JPAQueryFactory(entityManager)
}
