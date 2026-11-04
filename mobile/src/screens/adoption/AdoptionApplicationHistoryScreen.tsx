import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useEffect, useState } from 'react';
import { Image, Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';

import type { RootStackParamList } from '../../app/navigation/types';
import CheckIcon from '../../assets/icons/common/check.svg';
import CancelIcon from '../../assets/icons/common/cancel.svg';
import FormIcon from '../../assets/icons/common/form.svg';
import RightArrowIcon from '../../assets/icons/common/right-arrow.svg';
import { AppHeader, Button } from '../../components/common';
import {
  type AdoptionApplicationRecord,
  type AdoptionApplicationStatus,
  getAdoptionApplicationRecord,
  updateAdoptionApplicationStatus,
} from '../../services/storage/adoptionApplicationStorage';
import {
  getAdoptionApplicationDraft,
  removeAdoptionApplicationDraft,
} from '../../services/storage/adoptionDraftStorage';
import { colors, radius, shadows, typography } from '../../theme';

type Props = NativeStackScreenProps<RootStackParamList, 'AdoptionApplicationHistory'>;

type StatusPresentation = {
  activeStep: number;
  description: string;
  illustration: number;
  title: string;
};

export const applicationStatusPresentation: Record<AdoptionApplicationStatus, StatusPresentation> =
  {
    DRAFT: {
      activeStep: 0,
      description: '작성 내용을 확인하고 신청서를 제출해주세요',
      illustration: require('../../assets/images/adoption/process1.png'),
      title: '작성중인 신청서입니다',
    },
    SUBMITTED: {
      activeStep: 1,
      description: '보호소에서 신청서를 확인하고 있습니다',
      illustration: require('../../assets/images/adoption/process2.png'),
      title: '신청이 접수되었어요!',
    },
    DOCUMENT_APPROVED: {
      activeStep: 2,
      description: '보호소와 면담 일정을 조율하고 있습니다',
      illustration: require('../../assets/images/adoption/process3.png'),
      title: '입양 심사가 진행 중이에요',
    },
    ADOPTION_APPROVED: {
      activeStep: 3,
      description: '면담 후 최종 입양이 완료되었습니다',
      illustration: require('../../assets/images/adoption/process4.png'),
      title: '최종 입양이 승인되었어요!',
    },
    REJECTED: {
      activeStep: 2,
      description: '심사 결과 및 거절 사유에 대한\n자세한 내용은 보호소에 문의해 주세요',
      illustration: require('../../assets/images/adoption/process_refused.png'),
      title: '입양이 거절되었어요',
    },
    CANCELED: {
      activeStep: 0,
      description: '신청자가 직접 신청을 취소하여\n더 이상 심사가 진행되지 않습니다',
      illustration: require('../../assets/images/adoption/process_failed.png'),
      title: '입양 신청이 취소되었어요',
    },
  };

const progressLabels = ['신청서 작성', '서류 심사', '입양 심사', '최종 승인'];

function formatDate(value?: string) {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}.${month}.${day}`;
}

function ProcessProgress({ activeStep }: { activeStep: number }) {
  return (
    <View style={styles.processWrap}>
      <View style={styles.processLine}>
        <View style={[styles.processActiveLine, { width: `${(activeStep / 3) * 100}%` }]} />
      </View>
      <View style={styles.processSteps}>
        {progressLabels.map((label, index) => {
          const finalApproved = activeStep === progressLabels.length - 1;
          const completed = index < activeStep || (finalApproved && index === activeStep);
          const active = index === activeStep && !finalApproved;
          return (
            <View key={label} style={styles.processStep}>
              <View
                style={[
                  styles.processDot,
                  (completed || active) && styles.processDotActive,
                  active && styles.processDotCurrent,
                ]}
              >
                {completed ? <CheckIcon height={10} width={10} /> : null}
              </View>
              <Text
                style={[styles.processLabel, (completed || active) && styles.processLabelActive]}
              >
                {label}
              </Text>
            </View>
          );
        })}
      </View>
    </View>
  );
}

export function AdoptionApplicationHistoryScreen({ navigation, route }: Props) {
  const insets = useSafeAreaInsets();
  const [record, setRecord] = useState<AdoptionApplicationRecord | null>(null);
  const [status, setStatus] = useState<AdoptionApplicationStatus>('DRAFT');
  const [showConfirm, setShowConfirm] = useState(false);
  const [showRejectionReason, setShowRejectionReason] = useState(false);

  useEffect(() => {
    Promise.all([getAdoptionApplicationRecord(), getAdoptionApplicationDraft()]).then(
      ([storedRecord, draft]) => {
        if (route.params.previewStatus) {
          setRecord(
            route.params.previewStatus === 'DRAFT'
              ? null
              : storedRecord ?? {
                  animalId: route.params.animalId,
                  applicantName: '우사기',
                  applicationNumber: '113122600015',
                  form: {},
                  rejectionReason:
                    '신청자님의 양육 환경과 현재 보호동물의 특성을 종합적으로 검토한 결과, 안정적인 적응이 어렵다고 판단되었습니다.',
                  shelterName: '박범준보호소',
                  status: route.params.previewStatus,
                  submittedAt: '2026-10-27T00:00:00.000Z',
                  version: 1,
                },
          );
          setStatus(route.params.previewStatus);
        } else if (route.params.source === 'draft' && draft) {
          setRecord(null);
          setStatus('DRAFT');
        } else if (storedRecord) {
          setRecord(storedRecord);
          setStatus(storedRecord.status);
        } else if (draft) {
          setStatus('DRAFT');
        }
      },
    );
  }, [route.params.animalId, route.params.previewStatus, route.params.source]);

  const presentation = applicationStatusPresentation[status];
  const isDraft = status === 'DRAFT';
  const isPreview = Boolean(route.params.previewStatus);
  const canCancel = status === 'SUBMITTED';
  const isCanceled = status === 'CANCELED';
  const isRejected = status === 'REJECTED';
  const isApproved = status === 'ADOPTION_APPROVED';

  const handleDocumentPress = () => {
    if (isDraft) {
      navigation.navigate('AdoptionApplication', { animalId: route.params.animalId });
      return;
    }
    navigation.navigate('AdoptionApplicationReview', { animalId: route.params.animalId });
  };

  const handleDeleteOrCancel = async () => {
    if (isPreview) {
      setShowConfirm(false);
      if (!isDraft) {
        setRecord(current => (current ? { ...current, status: 'CANCELED' } : current));
        setStatus('CANCELED');
      }
      return;
    }
    if (isDraft) {
      await removeAdoptionApplicationDraft();
      setShowConfirm(false);
      navigation.goBack();
      return;
    }
    const updated = await updateAdoptionApplicationStatus('CANCELED');
    if (updated) {
      setRecord(updated);
      setStatus('CANCELED');
    }
    setShowConfirm(false);
  };

  return (
    <SafeAreaView edges={['top']} style={styles.safeArea}>
      <AppHeader onBackPress={navigation.goBack} title="입양 신청 내역" />
      <View style={styles.statusArea}>
        <View
          style={[
            styles.statusCopy,
            isCanceled && styles.canceledStatusCopy,
            isRejected && styles.rejectedStatusCopy,
          ]}
        >
          <Text style={styles.statusTitle}>{presentation.title}</Text>
          <Text style={styles.statusDescription}>{presentation.description}</Text>
        </View>
        <Image
          source={presentation.illustration}
          style={[
            styles.processImage,
            isApproved && styles.approvedProcessImage,
            isCanceled && styles.canceledProcessImage,
            isRejected && styles.rejectedProcessImage,
          ]}
        />
        {!isCanceled && !isRejected ? (
          <ProcessProgress activeStep={presentation.activeStep} />
        ) : null}
      </View>
      <View style={[styles.sheet, { paddingBottom: insets.bottom }]}>
        <View style={styles.animalRow}>
          <Image
            source={require('../../assets/images/adoption/animal-detail.jpg')}
            style={styles.animalImage}
          />
          <View style={styles.animalText}>
            <Text style={styles.animalTitle}>[개] 믹스견</Text>
            <Text style={styles.animalMeta}>2026년생 | 암컷 | 3kg</Text>
            <Text style={styles.animalMeta}>
              중성화 여부 <Text style={styles.neutered}>O</Text>
            </Text>
          </View>
        </View>
        <View style={styles.registrationRows}>
          <View style={styles.infoRow}>
            <Text style={styles.infoLabel}>등록번호</Text>
            <Text style={styles.infoValue}>113122600015</Text>
          </View>
          <View style={styles.infoRow}>
            <Text style={styles.infoLabel}>마이크로칩번호</Text>
            <Text style={styles.infoValue}>410987654321098</Text>
          </View>
        </View>
        <Pressable
          accessibilityRole="button"
          onPress={() => navigation.navigate('AdoptionDetail', { animalId: route.params.animalId })}
          style={styles.animalDetailLink}
        >
          <Text style={styles.animalDetailText}>보호동물 상세보기</Text>
          <RightArrowIcon height={19} width={19} />
        </Pressable>
        <View style={styles.divider} />
        <Text style={styles.sectionTitle}>신청 정보</Text>
        {[
          ['신청 번호', record?.applicationNumber ?? '-'],
          ['신청일', formatDate(record?.submittedAt)],
          ['신청자', record?.applicantName ?? '-'],
          ['보호소', record?.shelterName ?? '-'],
        ].map(([label, value]) => (
          <View key={label} style={styles.infoRow}>
            <Text style={styles.infoLabel}>{label}</Text>
            <Text style={styles.infoValue}>{value}</Text>
          </View>
        ))}
        <Pressable
          accessibilityRole="button"
          onPress={handleDocumentPress}
          style={styles.documentCard}
        >
          <View style={styles.documentIconCircle}>
            <FormIcon height={25} width={25} />
          </View>
          <View style={styles.documentCopy}>
            <Text style={styles.documentTitle}>입양 신청서</Text>
            <Text style={styles.documentDescription}>작성한 입양 신청서 확인</Text>
          </View>
          <RightArrowIcon height={24} width={24} />
        </Pressable>
        {isDraft || canCancel || isRejected ? (
          <Pressable
            accessibilityRole="button"
            onPress={() => (isRejected ? setShowRejectionReason(true) : setShowConfirm(true))}
            style={styles.bottomAction}
          >
            <Text style={styles.bottomActionText}>
              {isRejected ? '거절 사유 확인' : isDraft ? '입양신청서 삭제' : '제출 취소'}
            </Text>
          </Pressable>
        ) : null}
      </View>
      <Modal animationType="fade" transparent visible={showConfirm}>
        <View style={styles.overlay}>
          <View style={styles.modalCard}>
            <Text style={styles.modalTitle}>
              {isDraft ? '임시저장한 신청서를 삭제할까요?' : '제출한 신청서를 취소할까요?'}
            </Text>
            <Text style={styles.modalDescription}>
              {isDraft
                ? '삭제 후에는 작성한 내용을 복구할 수 없습니다.'
                : '취소 후에는 신청서를 다시 수정할 수 없습니다.'}
            </Text>
            <View style={styles.modalActions}>
              <Button
                onPress={() => setShowConfirm(false)}
                style={[styles.modalButton, styles.closeButton]}
              >
                닫기
              </Button>
              <Button
                onPress={handleDeleteOrCancel}
                style={[styles.modalButton, styles.dangerButton]}
              >
                {isDraft ? '삭제하기' : '제출 취소'}
              </Button>
            </View>
          </View>
        </View>
      </Modal>
      <Modal
        animationType="fade"
        onRequestClose={() => setShowRejectionReason(false)}
        transparent
        visible={showRejectionReason}
      >
        <View style={[styles.overlay, styles.rejectionOverlay]}>
          <View style={styles.rejectionModalCard}>
            <View style={styles.rejectionModalHeader}>
              <Text style={styles.rejectionModalTitle}>거절 사유 확인</Text>
              <Pressable
                accessibilityLabel="거절 사유 닫기"
                accessibilityRole="button"
                hitSlop={12}
                onPress={() => setShowRejectionReason(false)}
                style={styles.rejectionClose}
              >
                <CancelIcon height={16} width={16} />
              </Pressable>
            </View>
            <Text style={styles.rejectionReason}>
              {record?.rejectionReason ??
                '심사 결과, 현재 신청 정보만으로는 보호동물에게 적합한 양육 환경을 확인하기 어려워 입양 신청이 승인되지 않았습니다. 자세한 내용은 보호소에 문의해 주세요.'}
            </Text>
          </View>
        </View>
      </Modal>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: { backgroundColor: colors.white, flex: 1 },
  statusArea: { height: 203, paddingHorizontal: 36, paddingTop: 44 },
  statusCopy: { marginLeft: 19, maxWidth: 250 },
  canceledStatusCopy: {
    left: 36,
    marginLeft: 0,
    maxWidth: 180,
    position: 'absolute',
    top: 103,
  },
  rejectedStatusCopy: {
    left: 36,
    marginLeft: 0,
    maxWidth: 190,
    position: 'absolute',
    top: 124,
  },
  statusTitle: { ...typography.bodySemiBold16, color: colors.gray600 },
  statusDescription: { ...typography.captionSemiBold12, color: colors.gray500, marginTop: 6 },
  processImage: { height: 93, position: 'absolute', right: 38, top: 32, width: 93 },
  approvedProcessImage: { height: 104, right: 32, top: 32, width: 104 },
  canceledProcessImage: { height: 141, right: 46, top: 37, width: 141 },
  rejectedProcessImage: { height: 160, right: 42, top: 37, width: 160 },
  processWrap: { height: 72, marginTop: 33 },
  processLine: {
    backgroundColor: colors.gray200,
    height: 2,
    left: 28,
    position: 'absolute',
    right: 28,
    top: 8,
  },
  processActiveLine: {
    backgroundColor: '#FA705B',
    height: 2,
  },
  processSteps: { flexDirection: 'row', justifyContent: 'space-between', marginHorizontal: -7 },
  processStep: { alignItems: 'center', width: 70 },
  processDot: {
    alignItems: 'center',
    backgroundColor: colors.gray200,
    borderRadius: 9,
    height: 18,
    justifyContent: 'center',
    width: 18,
  },
  processDotActive: { backgroundColor: '#FA705B' },
  processDotCurrent: { borderColor: '#FA705B', borderWidth: 5, backgroundColor: colors.white },
  processLabel: {
    ...typography.captionSemiBold12,
    color: colors.gray500,
    fontFamily: 'Pretendard-Medium',
    marginTop: 20,
  },
  processLabelActive: { color: '#FA705B' },
  sheet: {
    ...shadows.level2,
    backgroundColor: colors.white,
    borderTopLeftRadius: 30,
    borderTopRightRadius: 30,
    flex: 1,
    marginHorizontal: 19,
    paddingHorizontal: 23,
    paddingTop: 24,
  },
  animalRow: { flexDirection: 'row' },
  animalImage: { borderRadius: radius.lg, height: 80, width: 80 },
  animalText: { marginLeft: 16 },
  animalTitle: { ...typography.titleBold18, color: colors.black, fontSize: 20, lineHeight: 26 },
  animalMeta: { ...typography.bodyRegular14, color: colors.black, marginTop: 2 },
  neutered: { color: colors.green400 },
  registrationRows: { gap: 7, marginTop: 14 },
  infoRow: { flexDirection: 'row', justifyContent: 'space-between', minHeight: 23 },
  infoLabel: { ...typography.bodyRegular14, color: colors.gray500 },
  infoValue: { ...typography.bodyRegular14, color: colors.black, textAlign: 'right' },
  animalDetailLink: {
    alignItems: 'center',
    alignSelf: 'flex-end',
    flexDirection: 'row',
    marginTop: 10,
  },
  animalDetailText: { ...typography.bodyRegular14, color: colors.gray500 },
  divider: { backgroundColor: colors.gray300, height: 1, marginVertical: 13 },
  sectionTitle: { ...typography.bodySemiBold16, color: colors.gray800, marginBottom: 8 },
  documentCard: {
    alignItems: 'center',
    borderColor: colors.gray300,
    borderRadius: 10,
    borderWidth: 1,
    flexDirection: 'row',
    height: 64,
    marginTop: 20,
    paddingHorizontal: 10,
  },
  documentIconCircle: {
    alignItems: 'center',
    backgroundColor: '#FFD9A6',
    borderRadius: 22,
    height: 44,
    justifyContent: 'center',
    width: 44,
  },
  documentCopy: { flex: 1, marginLeft: 16 },
  documentTitle: { ...typography.bodySemiBold16, color: colors.gray600 },
  documentDescription: { ...typography.captionSemiBold12, color: colors.gray500, marginTop: 2 },
  bottomAction: { alignItems: 'center', marginTop: 'auto', paddingBottom: 28, paddingTop: 20 },
  bottomActionText: { ...typography.bodyRegular14, color: colors.gray500 },
  overlay: {
    alignItems: 'center',
    backgroundColor: 'rgba(0,0,0,0.35)',
    flex: 1,
    justifyContent: 'center',
    paddingHorizontal: 26,
  },
  modalCard: { backgroundColor: colors.white, borderRadius: 24, padding: 24, width: '100%' },
  modalTitle: { ...typography.titleBold18, color: colors.gray800 },
  modalDescription: { ...typography.bodyRegular14, color: colors.gray500, marginTop: 10 },
  modalActions: { flexDirection: 'row', gap: 12, marginTop: 22 },
  modalButton: { flex: 1 },
  closeButton: { backgroundColor: colors.gray200 },
  dangerButton: { backgroundColor: '#FA705B' },
  rejectionOverlay: { paddingHorizontal: 24 },
  rejectionModalCard: {
    backgroundColor: colors.white,
    borderRadius: 18,
    padding: 24,
    width: '100%',
  },
  rejectionModalHeader: { alignItems: 'center', flexDirection: 'row', gap: 12 },
  rejectionModalTitle: {
    color: '#222222',
    flex: 1,
    fontFamily: 'Pretendard-Bold',
    fontSize: 22,
    lineHeight: 28,
  },
  rejectionClose: { height: 16, width: 16 },
  rejectionReason: {
    ...typography.bodyRegular16,
    color: colors.gray500,
    lineHeight: 20.5,
    marginTop: 16,
  },
});
