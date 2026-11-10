import { useEffect, useState } from 'react';
import { ActivityIndicator, Alert, Modal, Pressable, StyleSheet, Text, View } from 'react-native';

import ReportCloseIcon from '../../assets/icons/common/report-close.svg';
import ReportPawIcon from '../../assets/icons/common/report-paw.svg';
import { ApiClientError, getApiErrorDisplayMessage } from '../../services/api';
import {
  createReport,
  REPORT_REASON_CODE_BY_LABEL,
  type ReportTargetType,
} from '../../services/reports';
import { colors, typography } from '../../theme';

const POST_REASONS = ['허위 정보 / 잘못된 동물 정보', '스팸 / 광고', '욕설 / 불쾌한 내용', '기타'];

const COMMENT_REASONS = ['욕설 / 비방 / 괴롭힘', '스팸 / 광고', '음란 / 부적절한 내용', '기타'];

export type ContentReportTargetType = 'post' | 'comment' | 'reply' | 'user';

export type ContentReportTarget = {
  excerpt?: string;
  id: string;
  nickname?: string;
  targetSeq: number;
  targetType: ReportTargetType;
  type: ContentReportTargetType;
};

type Props = {
  onClose: () => void;
  target: ContentReportTarget | null;
};

export function ContentReportModal({ onClose, target }: Props) {
  const [reason, setReason] = useState('');
  const [alreadyReported, setAlreadyReported] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (target) {
      setReason('');
      setAlreadyReported(false);
    }
  }, [target]);

  if (!target) {
    return null;
  }

  const targetName = { post: '게시글', reply: '답글', comment: '댓글', user: '사용자' }[
    target.type
  ];
  const reasons = target.type === 'post' ? POST_REASONS : COMMENT_REASONS;
  const canSubmit = Boolean(reason);
  const submit = async () => {
    const reasonCode = REPORT_REASON_CODE_BY_LABEL[reason];
    if (!reasonCode || submitting) {
      return;
    }
    try {
      setSubmitting(true);
      await createReport({ reasonCode, targetSeq: target.targetSeq, targetType: target.targetType });
      onClose();
    } catch (error) {
      if (error instanceof ApiClientError && error.code === 'REPORT_ALREADY_EXISTS') {
        setAlreadyReported(true);
      } else {
        Alert.alert(
          '신고를 접수하지 못했어요',
          getApiErrorDisplayMessage(error, '잠시 후 다시 시도해 주세요.'),
        );
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal animationType="fade" onRequestClose={onClose} transparent visible>
      <View style={styles.backdrop}>
        <Pressable onPress={onClose} style={StyleSheet.absoluteFill} />
        <View
          accessibilityViewIsModal
          style={[styles.card, alreadyReported && styles.duplicateCard]}
        >
          {alreadyReported ? (
            <>
              <Text style={styles.duplicateTitle}>{`이미 신고한 ${targetName}입니다.`}</Text>
              <Text style={styles.duplicateDescription}>
                {target.type === 'user'
                  ? '같은 사용자는 중복으로 신고할 수 없습니다.'
                  : '같은 콘텐츠는 중복으로 신고할 수 없습니다.'}
              </Text>
              <Pressable onPress={onClose} style={styles.submitButton}>
                <Text style={styles.submitText}>확인</Text>
              </Pressable>
            </>
          ) : (
            <>
              <View style={styles.header}>
                <Text style={styles.title}>{`${targetName} 신고`}</Text>
                <Pressable accessibilityLabel="신고 창 닫기" hitSlop={10} onPress={onClose}>
                  <ReportCloseIcon height={16} width={16} />
                </Pressable>
              </View>
              <Text style={styles.guide}>신고 사유를 선택해주세요</Text>
              {target.type === 'user' || (target.type !== 'post' && target.excerpt) ? (
                <View style={styles.targetCard}>
                  <Text numberOfLines={1} style={styles.targetMeta}>
                    {`신고 대상 · ${target.nickname ?? '사용자'}`}
                  </Text>
                  {target.excerpt && (
                    <Text numberOfLines={2} style={styles.targetExcerpt}>
                      {target.excerpt}
                    </Text>
                  )}
                </View>
              ) : null}
              <View style={styles.reasonList}>
                {reasons.map(item => {
                  const selected = item === reason;
                  return (
                    <Pressable
                      accessibilityRole="radio"
                      accessibilityState={{ checked: selected }}
                      key={item}
                      onPress={() => setReason(item)}
                      style={[styles.reason, selected && styles.selectedReason]}
                    >
                      <Text style={[styles.reasonText, selected && styles.selectedReasonText]}>
                        {item}
                      </Text>
                      {selected ? <ReportPawIcon height={20} width={20} /> : null}
                    </Pressable>
                  );
                })}
              </View>
              <Pressable
                disabled={!canSubmit || submitting}
                onPress={submit}
                style={[styles.submitButton, (!canSubmit || submitting) && styles.disabledButton]}
              >
                {submitting ? (
                  <ActivityIndicator color={colors.white} />
                ) : (
                  <Text style={styles.submitText}>신고하기</Text>
                )}
              </Pressable>
            </>
          )}
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    alignItems: 'center',
    backgroundColor: 'rgba(0, 0, 0, 0.3)',
    flex: 1,
    justifyContent: 'center',
    paddingHorizontal: 24,
  },
  card: { backgroundColor: colors.white, borderRadius: 18, padding: 24, width: '100%' },
  duplicateCard: { minHeight: 168 },
  header: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between' },
  title: { ...typography.titleSemiBold18, color: colors.gray800, fontSize: 22, lineHeight: 28 },
  guide: { ...typography.bodyRegular14, color: colors.gray500, marginTop: 16 },
  duplicateTitle: {
    ...typography.titleSemiBold18,
    color: colors.gray800,
  },
  duplicateDescription: { ...typography.bodyRegular14, color: colors.gray500, marginTop: 16 },
  targetCard: {
    backgroundColor: colors.white,
    borderColor: colors.gray200,
    borderRadius: 8,
    borderWidth: 1,
    marginTop: 12,
    padding: 12,
  },
  targetMeta: { ...typography.captionRegular10, color: '#FA705B' },
  targetExcerpt: { ...typography.bodyRegular14, color: colors.gray600, marginTop: 5 },
  reasonList: { gap: 8, marginTop: 14 },
  reason: {
    alignItems: 'center',
    backgroundColor: colors.gray10,
    borderRadius: 8,
    flexDirection: 'row',
    height: 44,
    justifyContent: 'space-between',
    paddingHorizontal: 12,
  },
  selectedReason: { backgroundColor: 'rgba(252, 149, 149, 0.2)' },
  reasonText: { ...typography.bodyRegular14, color: colors.gray600 },
  selectedReasonText: {
    ...typography.bodySemiBold16,
    color: '#FA705B',
    fontSize: 14,
    lineHeight: 18,
  },
  submitButton: {
    alignItems: 'center',
    backgroundColor: '#FA705B',
    borderRadius: 8,
    height: 48,
    justifyContent: 'center',
    marginTop: 16,
  },
  disabledButton: { backgroundColor: colors.gray200 },
  submitText: { ...typography.bodySemiBold16, color: colors.white },
});
