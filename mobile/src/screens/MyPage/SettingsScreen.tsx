import AsyncStorage from '@react-native-async-storage/async-storage';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  AppState,
  Linking,
  Modal,
  PermissionsAndroid,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import type { RootStackParamList } from '../../app/navigation/types';
import FilterArrowIcon from '../../assets/icons/common/filter-arrow.svg';
import LeftArrowIcon from '../../assets/icons/common/left-arrow.svg';
import ChevronRightIcon from '../../assets/icons/mypage/chevron-right.svg';
import { TimePickerModal } from '../../components/common';
import { useAdoptionBookmarkStore } from '../../stores/adoptionBookmarkStore';
import {
  type NotificationSettingKey,
  useNotificationSettingsStore,
} from '../../stores/notificationSettingsStore';
import { usePetStore } from '../../stores/petStore';
import { clearSimulationReports } from '../../services/simulation/reports';
import { useProfileStore } from '../../stores/profileStore';
import { colors, radius, typography } from '../../theme';
import { useAuthStore } from '../../stores/authStore';
import { getApiErrorDisplayMessage } from '../../services/api';
import { deactivateDeviceTokenIfSignedIn } from '../../services/notifications/deviceTokenRegistration';
import { getStoredFcmToken } from '../../services/notifications/firebaseMessaging';

type Props = NativeStackScreenProps<RootStackParamList, 'Settings'>;
type AccountModal = 'logout' | 'withdraw' | null;

const notificationItems: Array<{
  description: string;
  key: NotificationSettingKey;
  label: string;
}> = [
  { description: '돌봄 활동 및 시뮬레이션 진행 알림', key: 'mockAdoption', label: '모의입양 알림' },
  { description: '새로운 메시지 수신 알림', key: 'chat', label: '채팅 알림' },
  { description: '내 게시글에 새로운 댓글 등록 알림', key: 'comment', label: '댓글 알림' },
  { description: '실종·발견동물 유사 매칭 알림', key: 'aiMatching', label: 'AI 매칭 알림' },
  { description: '입양 신청 및 진행 상태 안내', key: 'adoption', label: '입양 관련 알림' },
];

export function SettingsScreen({ navigation }: Props) {
  const settings = useNotificationSettingsStore();
  const resetProfile = useProfileStore(state => state.resetProfile);
  const resetPets = usePetStore(state => state.resetPets);
  const resetBookmarks = useAdoptionBookmarkStore(state => state.reset);
  const logout = useAuthStore(state => state.logout);
  const withdraw = useAuthStore(state => state.withdraw);
  const [notificationsAllowed, setNotificationsAllowed] = useState(true);
  const [accountModal, setAccountModal] = useState<AccountModal>(null);
  const [activeTimeField, setActiveTimeField] = useState<'start' | 'end' | null>(null);

  const readNotificationPermission = useCallback(async () => {
    if (Platform.OS !== 'android' || Number(Platform.Version) < 33) {
      setNotificationsAllowed(true);
      return;
    }
    try {
      setNotificationsAllowed(
        await PermissionsAndroid.check(PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS),
      );
    } catch {
      setNotificationsAllowed(false);
    }
  }, []);

  useEffect(() => {
    if (notificationsAllowed) {
      return;
    }
    getStoredFcmToken()
      .then(token => deactivateDeviceTokenIfSignedIn(token))
      .catch(() => {
        console.warn('[FCM] 기기 토큰 해제에 실패했습니다.');
      });
  }, [notificationsAllowed]);

  useEffect(() => {
    readNotificationPermission();
    const subscription = AppState.addEventListener('change', state => {
      if (state === 'active') {
        readNotificationPermission();
      }
    });
    return () => subscription.remove();
  }, [readNotificationPermission]);

  const resetUserState = () => {
    resetProfile();
    resetPets();
    resetBookmarks();
  };

  const openTimePicker = (field: 'start' | 'end') => {
    setActiveTimeField(field);
  };

  const selectedTime = new Date();
  const savedTime =
    activeTimeField === 'end' ? settings.doNotDisturbEnd : settings.doNotDisturbStart;
  const [selectedHour = 0, selectedMinute = 0] = savedTime.split(':').map(Number);
  selectedTime.setHours(selectedHour, selectedMinute, 0, 0);

  const completeLogout = async () => {
    try {
      await logout();
    } finally {
      clearSimulationReports();
      resetUserState();
      setAccountModal(null);
    }
  };

  const completeWithdrawal = async () => {
    try {
      await withdraw();
      await AsyncStorage.clear();
      clearSimulationReports();
      resetUserState();
      settings.resetSettings();
      setAccountModal(null);
    } catch (error) {
      Alert.alert(
        '회원 탈퇴 실패',
        getApiErrorDisplayMessage(error, '잠시 후 다시 시도해 주세요.'),
      );
    }
  };

  return (
    <SafeAreaView style={styles.safeArea}>
      <View style={styles.header}>
        <Pressable
          accessibilityLabel="뒤로가기"
          hitSlop={10}
          onPress={navigation.goBack}
          style={styles.backButton}
        >
          <LeftArrowIcon height={24} width={24} />
        </Pressable>
        <Text style={styles.headerTitle}>환경설정</Text>
      </View>

      <ScrollView
        contentContainerStyle={styles.content}
        decelerationRate="fast"
        showsVerticalScrollIndicator={false}
      >
        <Text style={styles.sectionTitle}>알림 설정</Text>
        <View style={styles.settingsBody}>
          <Pressable
            accessibilityLabel="기기 알림 설정 열기"
            accessibilityRole="button"
            onPress={() => Linking.openSettings()}
            style={styles.deviceSetting}
          >
            <View style={styles.settingTextWrap}>
              <Text style={styles.settingLabel}>기기 알림 설정</Text>
              <Text style={styles.settingDescription}>휴대폰의 USEE 앱 알림 권한을 관리해요.</Text>
            </View>
            <Text style={[styles.permissionStatus, !notificationsAllowed && styles.blockedStatus]}>
              {notificationsAllowed ? '허용' : '차단됨'}
            </Text>
            <ChevronRightIcon height={20} width={20} />
          </Pressable>

          <View style={styles.divider} />

          {notificationItems.map(item => (
            <View key={item.key}>
              <NotificationRow
                description={item.description}
                disabled={!notificationsAllowed}
                label={item.label}
                onPress={() => settings.toggleSetting(item.key)}
                value={settings[item.key]}
              />
              {item.key === 'mockAdoption' && settings.mockAdoption && notificationsAllowed ? (
                <View style={styles.doNotDisturb}>
                  <Text style={styles.doNotDisturbTitle}>방해금지 시간 설정</Text>
                  <View style={styles.timeRow}>
                    <TimeSelect
                      label="시작 시간"
                      onPress={() => openTimePicker('start')}
                      value={settings.doNotDisturbStart}
                    />
                    <TimeSelect
                      label="종료 시간"
                      onPress={() => openTimePicker('end')}
                      value={settings.doNotDisturbEnd}
                    />
                  </View>
                </View>
              ) : null}
            </View>
          ))}
        </View>

        <View style={styles.accountSection}>
          <Text style={styles.sectionTitle}>계정 관리</Text>
          <Pressable
            accessibilityRole="button"
            onPress={() => setAccountModal('logout')}
            style={[styles.accountRow, styles.firstAccountRow]}
          >
            <Text style={styles.accountText}>로그아웃</Text>
          </Pressable>
          <Pressable
            accessibilityRole="button"
            onPress={() => setAccountModal('withdraw')}
            style={styles.accountRow}
          >
            <Text style={styles.accountText}>회원탈퇴</Text>
          </Pressable>
        </View>
      </ScrollView>

      <AccountConfirmModal
        onCancel={() => setAccountModal(null)}
        onConfirm={accountModal === 'withdraw' ? completeWithdrawal : completeLogout}
        type={accountModal}
      />
      <TimePickerModal
        onClose={() => setActiveTimeField(null)}
        onConfirm={time => {
          const formatted = `${String(time.getHours()).padStart(2, '0')}:${String(
            time.getMinutes(),
          ).padStart(2, '0')}`;
          if (activeTimeField === 'start') {
            settings.setDoNotDisturbStart(formatted);
          } else if (activeTimeField === 'end') {
            settings.setDoNotDisturbEnd(formatted);
          }
          setActiveTimeField(null);
        }}
        title={activeTimeField === 'end' ? '종료 시간 선택' : '시작 시간 선택'}
        value={selectedTime}
        visible={activeTimeField !== null}
      />
    </SafeAreaView>
  );
}

type NotificationRowProps = {
  description: string;
  disabled: boolean;
  label: string;
  onPress: () => void;
  value: boolean;
};

function NotificationRow({ description, disabled, label, onPress, value }: NotificationRowProps) {
  return (
    <View style={[styles.notificationRow, disabled && styles.disabledRow]}>
      <View>
        <Text style={styles.settingLabel}>{label}</Text>
        <Text style={styles.settingDescription}>{description}</Text>
      </View>
      <Pressable
        accessibilityLabel={`${label} ${value ? '끄기' : '켜기'}`}
        accessibilityRole="switch"
        accessibilityState={{ checked: value, disabled }}
        disabled={disabled}
        onPress={onPress}
        style={[styles.switchTrack, value && !disabled && styles.switchTrackOn]}
      >
        <View style={[styles.switchThumb, value && styles.switchThumbOn]} />
      </Pressable>
    </View>
  );
}

type TimeSelectProps = {
  label: string;
  onPress: () => void;
  value: string;
};

function TimeSelect({ label, onPress, value }: TimeSelectProps) {
  return (
    <View style={styles.timeField}>
      <Text style={styles.timeLabel}>{label}</Text>
      <Pressable onPress={onPress} style={styles.timeButton}>
        <Text style={styles.timeValue}>{value || '선택'}</Text>
        <FilterArrowIcon height={20} width={20} />
      </Pressable>
    </View>
  );
}

type AccountConfirmModalProps = {
  onCancel: () => void;
  onConfirm: () => void;
  type: AccountModal;
};

function AccountConfirmModal({ onCancel, onConfirm, type }: AccountConfirmModalProps) {
  const withdrawal = type === 'withdraw';
  return (
    <Modal animationType="fade" onRequestClose={onCancel} transparent visible={type !== null}>
      <View style={styles.modalOverlay}>
        <View style={styles.modalCard}>
          <Text style={styles.modalTitle}>
            {withdrawal ? '정말 회원탈퇴를 하시겠습니까?' : '정말 로그아웃 하시겠습니까?'}
          </Text>
          <Text style={styles.modalDescription}>
            {withdrawal
              ? '탈퇴 시 계정 및 등록한 정보가 삭제되며,\n삭제된 정보는 복구할 수 없습니다.'
              : '로그아웃 후에는 다시 로그인해야\n서비스를 이용할 수 있습니다.'}
          </Text>
          <View style={styles.modalButtons}>
            {withdrawal ? (
              <>
                <Pressable onPress={onConfirm} style={[styles.modalButton, styles.neutralButton]}>
                  <Text style={styles.modalButtonText}>탈퇴하기</Text>
                </Pressable>
                <Pressable onPress={onCancel} style={[styles.modalButton, styles.accentButton]}>
                  <Text style={styles.modalButtonText}>취소</Text>
                </Pressable>
              </>
            ) : (
              <>
                <Pressable onPress={onCancel} style={[styles.modalButton, styles.neutralButton]}>
                  <Text style={styles.modalButtonText}>취소</Text>
                </Pressable>
                <Pressable onPress={onConfirm} style={[styles.modalButton, styles.accentButton]}>
                  <Text style={styles.modalButtonText}>로그아웃</Text>
                </Pressable>
              </>
            )}
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  safeArea: { backgroundColor: colors.white, flex: 1 },
  header: { alignItems: 'center', height: 56, justifyContent: 'center' },
  backButton: { left: 18, position: 'absolute' },
  headerTitle: { ...typography.titleSemiBold18, color: colors.black },
  content: { paddingBottom: 40, paddingHorizontal: 26, paddingTop: 16 },
  sectionTitle: { ...typography.bodyRegular14, color: colors.gray500 },
  settingsBody: { marginLeft: 8, marginTop: 24 },
  deviceSetting: { alignItems: 'center', flexDirection: 'row', minHeight: 40 },
  settingTextWrap: { flex: 1 },
  settingLabel: { ...typography.bodySemiBold16, color: colors.black },
  settingDescription: { ...typography.bodyRegular14, color: colors.gray500, marginTop: 2 },
  permissionStatus: {
    ...typography.bodySemiBold16,
    color: colors.blue,
    fontSize: 14,
    marginRight: 2,
  },
  blockedStatus: { color: '#FA705B' },
  divider: { backgroundColor: colors.gray200, height: 1, marginBottom: 22, marginTop: 22 },
  notificationRow: {
    alignItems: 'flex-start',
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 22,
  },
  disabledRow: { opacity: 0.45 },
  switchTrack: { backgroundColor: '#D2D5DA', borderRadius: 10, height: 20, padding: 2, width: 36 },
  switchTrackOn: { backgroundColor: colors.green400 },
  switchThumb: {
    backgroundColor: colors.white,
    borderRadius: 8,
    height: 16,
    width: 16,
  },
  switchThumbOn: { marginLeft: 16 },
  doNotDisturb: { marginBottom: 22, marginTop: -8, zIndex: 5 },
  doNotDisturbTitle: { ...typography.bodyRegular14, color: colors.gray500, marginBottom: 10 },
  timeRow: { flexDirection: 'row', gap: 25 },
  timeField: { flex: 1 },
  timeLabel: {
    color: colors.gray500,
    fontFamily: 'Pretendard-Regular',
    fontSize: 12,
    lineHeight: 15,
    marginBottom: 5,
  },
  timeButton: {
    alignItems: 'center',
    borderColor: colors.gray200,
    borderRadius: radius.md,
    borderWidth: 1,
    flexDirection: 'row',
    height: 40,
    justifyContent: 'space-between',
    paddingHorizontal: 10,
  },
  timeValue: { ...typography.bodyRegular16, color: colors.gray600 },
  accountSection: {
    borderTopColor: colors.gray200,
    borderTopWidth: 1,
    marginTop: 0,
    paddingTop: 40,
  },
  accountRow: { height: 42, justifyContent: 'center', paddingLeft: 10 },
  firstAccountRow: { marginTop: 14 },
  accountText: { ...typography.bodySemiBold16, color: colors.black },
  modalOverlay: {
    alignItems: 'center',
    backgroundColor: 'rgba(0, 0, 0, 0.3)',
    flex: 1,
    justifyContent: 'center',
    paddingHorizontal: 24,
  },
  modalCard: { backgroundColor: colors.white, borderRadius: 18, padding: 24, width: '100%' },
  modalTitle: {
    color: colors.gray800,
    fontFamily: 'Pretendard-Bold',
    fontSize: 22,
    lineHeight: 29,
  },
  modalDescription: {
    ...typography.bodyRegular16,
    color: colors.gray500,
    lineHeight: 24,
    marginTop: 16,
  },
  modalButtons: { flexDirection: 'row', gap: 14, marginTop: 20 },
  modalButton: {
    alignItems: 'center',
    borderRadius: radius.md,
    flex: 1,
    height: 40,
    justifyContent: 'center',
  },
  neutralButton: { backgroundColor: colors.gray200 },
  accentButton: { backgroundColor: '#FA705B' },
  modalButtonText: { ...typography.bodySemiBold16, color: colors.white },
});
