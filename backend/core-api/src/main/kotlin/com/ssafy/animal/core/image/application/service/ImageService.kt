package com.ssafy.animal.core.image.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.core.image.application.port.`in`.ConfirmedImage
import com.ssafy.animal.core.image.application.port.`in`.ImageUseCase
import com.ssafy.animal.core.image.application.port.`in`.IssueUploadUrlCommand
import com.ssafy.animal.core.image.application.port.`in`.UploadUrl
import com.ssafy.animal.core.image.application.port.out.ImageRepository
import com.ssafy.animal.core.image.application.port.out.ImageStorage
import com.ssafy.animal.core.image.application.port.out.UploadContext
import com.ssafy.animal.core.image.application.port.out.UploadContextStore
import com.ssafy.animal.core.image.domain.Image
import com.ssafy.animal.core.image.domain.ImageErrorCode
import com.ssafy.animal.core.image.domain.ImagePolicy
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Service
class ImageService(
    private val imageStorage: ImageStorage,
    private val imageRepository: ImageRepository,
    private val uploadContextStore: UploadContextStore,
) : ImageUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun issueUploadUrl(command: IssueUploadUrlCommand): UploadUrl {
        imageStorage.ensureConfigured()
        ImagePolicy.validate(command.contentType, command.byteSize)
        val key = ImagePolicy.newKey(command.contentType)
        val presigned = imageStorage.presignPut(key, command.contentType, command.byteSize)
        uploadContextStore.save(key, UploadContext(command.userSeq, command.contentType, command.fileName), presigned.expiresIn)
        return UploadUrl(presigned.url, key, presigned.expiresIn.seconds)
    }

    // @Transactional을 붙이지 않는다: 동시 중복 확정의 UNIQUE 위반을 여기서 잡아 404로 바꾸는데,
    // 트랜잭션 안에서 잡으면 트랜잭션이 롤백 전용이 되어 커밋 때 다시 예외가 난다.
    // 쓰기는 saveAndFlush 하나뿐이라 저장소의 자체 트랜잭션으로 충분하다.
    override fun confirm(userSeq: Long, imageKey: String): ConfirmedImage {
        imageStorage.ensureConfigured()
        val context = uploadContextStore.find(imageKey)?.takeIf { it.userSeq == userSeq }
            ?: throw BusinessException(ImageErrorCode.IMAGE_NOT_FOUND)
        // 클라이언트가 보낸 크기·MIME은 믿지 않고 실제 객체로 다시 검증한다
        val stored = imageStorage.head(imageKey) ?: throw BusinessException(ImageErrorCode.IMAGE_NOT_FOUND)
        val contentType = stored.contentType ?: ""
        ImagePolicy.validate(contentType, stored.byteSize)
        if (contentType != context.contentType) throw BusinessException(ImageErrorCode.IMAGE_UNSUPPORTED_TYPE)

        // 태그 교체는 INSERT 전에: 뒤에 하다 실패하면 행은 있는데 수명주기가 하루 뒤 객체를 지운다
        imageStorage.markConfirmed(imageKey)
        val image = try {
            imageRepository.saveAndFlush(
                Image(imageKey, contentType, context.fileName, stored.byteSize, Math.toIntExact(userSeq)),
            )
        } catch (e: DataIntegrityViolationException) {
            // 같은 키로 동시에 들어온 확정 중 늦은 쪽. 먼저 끝난 요청이 컨텍스트를 지우므로 이후 재요청과 같은 404
            throw BusinessException(ImageErrorCode.IMAGE_NOT_FOUND)
        }
        // 행은 이미 생겼으므로 컨텍스트 삭제 실패로 요청을 실패시키지 않는다(TTL 10분이 정리)
        runCatching { uploadContextStore.delete(imageKey) }
            .onFailure { log.warn("[image] 업로드 컨텍스트 삭제 실패 key={}", imageKey, it) }
        return ConfirmedImage(image, imageStorage.publicUrl(imageKey))
    }

    @Transactional
    override fun delete(imageSeq: Long) {
        val image = imageRepository.findById(imageSeq) ?: return
        imageRepository.delete(image)
        val key = image.imageKey
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                // 실패해도 재시도하지 않는다(고아 객체 허용, Q427)
                runCatching { imageStorage.delete(key) }
                    .onFailure { log.warn("[image] S3 객체 삭제 실패 key={}", key, it) }
            }
        })
    }
}
