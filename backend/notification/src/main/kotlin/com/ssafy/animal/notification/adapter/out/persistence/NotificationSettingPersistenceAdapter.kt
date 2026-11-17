package com.ssafy.animal.notification.adapter.out.persistence

import com.ssafy.animal.notification.application.port.out.NotificationSettingRepository
import com.ssafy.animal.notification.domain.NotificationSetting
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface NotificationSettingJpaRepository : JpaRepository<NotificationSetting, Int> {
    fun findByUserSeqIn(userSeqs: Collection<Int>): List<NotificationSetting>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from NotificationSetting s where s.userSeq = :userSeq")
    fun findForUpdate(@Param("userSeq") userSeq: Int): NotificationSetting?

    // 컬럼 기본값이 엔티티 기본값과 같다. 첫 토글이 연달아 와도 PK 충돌 없이 한 행만 생긴다
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        nativeQuery = true,
        value = "insert into user_notification_settings (user_seq) values (:userSeq) on conflict (user_seq) do nothing",
    )
    fun insertIfAbsent(@Param("userSeq") userSeq: Int): Int
}

@Repository
class NotificationSettingPersistenceAdapter(
    private val jpaRepository: NotificationSettingJpaRepository,
) : NotificationSettingRepository {

    override fun find(userSeq: Int): NotificationSetting? = jpaRepository.findById(userSeq).orElse(null)

    override fun findAll(userSeqs: Collection<Int>) =
        if (userSeqs.isEmpty()) emptyList() else jpaRepository.findByUserSeqIn(userSeqs)

    override fun getOrCreateForUpdate(userSeq: Int): NotificationSetting {
        jpaRepository.insertIfAbsent(userSeq)
        return checkNotNull(jpaRepository.findForUpdate(userSeq)) { "알림 설정 행 생성 직후 조회 실패 userSeq=$userSeq" }
    }
}
