import { Image, Pressable, StyleSheet, Text, View } from 'react-native';
import type { SvgProps } from 'react-native-svg';

import ApplicationListIcon from '../../assets/icons/mypage/application-list.svg';
import ChevronRightIcon from '../../assets/icons/mypage/chevron-right.svg';
import MyPostsIcon from '../../assets/icons/mypage/my-posts.svg';
import NoticeIcon from '../../assets/icons/mypage/notice.svg';
import PencilIcon from '../../assets/icons/mypage/pencil.svg';
import SavedPostsIcon from '../../assets/icons/mypage/saved-posts.svg';
import SettingsIcon from '../../assets/icons/mypage/settings.svg';
import { SkeletonBlock, SkeletonGroup } from '../../components/common';
import { useProfileStore } from '../../stores/profileStore';
import { colors, radius, typography } from '../../theme';

type Props = {
  onOpenMockAdoption: () => void;
  onOpenMyPosts: () => void;
  onOpenAdoptionApplications: () => void;
  onOpenPets: () => void;
  onOpenProfileEdit: () => void;
  onOpenNotices: () => void;
  onOpenSettings: () => void;
  onOpenSavedPosts: () => void;
};

export function MyPageScreen({
  onOpenAdoptionApplications,
  onOpenMockAdoption,
  onOpenMyPosts,
  onOpenPets,
  onOpenProfileEdit,
  onOpenNotices,
  onOpenSettings,
  onOpenSavedPosts,
}: Props) {
  const profile = useProfileStore(state => state.profile);
  const error = useProfileStore(state => state.error);
  const isLoaded = useProfileStore(state => state.isLoaded);
  const isLoading = useProfileStore(state => state.isLoading);
  const loadProfile = useProfileStore(state => state.loadProfile);

  if (isLoading && !isLoaded) {
    return <MyPageSkeleton />;
  }

  if (error && !isLoaded) {
    return (
      <View style={styles.loadState}>
        <Text style={styles.loadStateTitle}>프로필을 불러오지 못했어요</Text>
        <Text style={styles.loadStateDescription}>{error}</Text>
        <Pressable
          accessibilityRole="button"
          onPress={() => loadProfile(true).catch(() => undefined)}
          style={styles.retryButton}
        >
          <Text style={styles.retryButtonText}>다시 시도</Text>
        </Pressable>
      </View>
    );
  }
  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.headerTitle}>마이페이지</Text>
        <Pressable
          accessibilityLabel="환경설정"
          accessibilityRole="button"
          hitSlop={10}
          onPress={onOpenSettings}
          style={({ pressed }) => [styles.settingsButton, pressed && styles.pressed]}
        >
          <SettingsIcon />
        </Pressable>
      </View>

      <View style={styles.profileArea}>
        <Image
          accessibilityLabel="프로필 이미지"
          source={profile.image}
          style={styles.profileImage}
        />
        <Pressable
          accessibilityLabel="내 정보 수정"
          accessibilityRole="button"
          accessibilityState={{ disabled: profile.accountStatus !== 'ACTIVE' }}
          disabled={profile.accountStatus !== 'ACTIVE'}
          onPress={onOpenProfileEdit}
          style={({ pressed }) => [
            styles.nicknameButton,
            profile.accountStatus !== 'ACTIVE' && styles.disabledProfile,
            pressed && styles.pressed,
          ]}
        >
          <Text style={styles.nickname}>{profile.nickname}</Text>
          <PencilIcon />
        </Pressable>
        {profile.accountStatus === 'SUSPENDED' ? (
          <Text style={styles.suspendedText}>이용이 정지된 계정입니다</Text>
        ) : null}
      </View>

      <View style={styles.sheet}>
        <View style={styles.shortcuts}>
          <Pressable
            accessibilityLabel="반려동물 등록"
            accessibilityRole="button"
            onPress={onOpenPets}
            style={({ pressed }) => [
              styles.shortcut,
              styles.petShortcut,
              pressed && styles.shortcutPressed,
            ]}
          >
            <View style={styles.shortcutArtwork}>
              <View style={styles.artworkHalo} />
              <Image
                source={require('../../assets/images/mypage/img_pet_card.png')}
                style={styles.shortcutImage}
              />
            </View>
            <Text style={styles.shortcutText}>반려동물 등록</Text>
            <Text style={styles.shortcutDescription}>우리 아이 정보 관리</Text>
            <View style={styles.shortcutArrow}>
              <ChevronRightIcon width={16} height={16} />
            </View>
          </Pressable>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="모의입양"
            onPress={onOpenMockAdoption}
            style={({ pressed }) => [
              styles.shortcut,
              styles.adoptionShortcut,
              pressed && styles.shortcutPressed,
            ]}
          >
            <View style={styles.shortcutArtwork}>
              <View style={[styles.artworkHalo, styles.adoptionHalo]} />
              <Image
                source={require('../../assets/images/mypage/img_mock_adoption.png')}
                style={styles.shortcutImage}
              />
            </View>
            <Text style={styles.shortcutText}>모의입양</Text>
            <Text style={styles.shortcutDescription}>함께하는 일상 체험</Text>
            <View style={styles.shortcutArrow}>
              <ChevronRightIcon width={16} height={16} />
            </View>
          </Pressable>
        </View>

        <View style={styles.menuList}>
          <MenuRow
            icon={ApplicationListIcon}
            label="입양 신청"
            onPress={onOpenAdoptionApplications}
          />
          <MenuRow icon={MyPostsIcon} label="내가 작성한 게시글" onPress={onOpenMyPosts} />
          <MenuRow icon={SavedPostsIcon} label="저장한 게시글" onPress={onOpenSavedPosts} />
          <MenuRow icon={NoticeIcon} label="공지사항" onPress={onOpenNotices} />
        </View>
      </View>
    </View>
  );
}

function MyPageSkeleton() {
  return (
    <SkeletonGroup accessibilityLabel="마이페이지 불러오는 중" style={styles.skeletonContainer}>
      <View style={styles.skeletonHeader}>
        <SkeletonBlock style={styles.skeletonHeaderTitle} />
        <SkeletonBlock style={styles.skeletonSettings} />
      </View>
      <View style={styles.skeletonProfile}>
        <SkeletonBlock style={styles.skeletonAvatar} />
        <SkeletonBlock style={styles.skeletonNickname} />
      </View>
      <View style={styles.skeletonSheet}>
        <View style={styles.skeletonShortcuts}>
          {[0, 1].map(index => (
            <View key={index} style={styles.skeletonShortcut}>
              <SkeletonBlock style={styles.skeletonArtwork} />
              <SkeletonBlock style={styles.skeletonShortcutTitle} />
              <SkeletonBlock style={styles.skeletonShortcutDescription} />
            </View>
          ))}
        </View>
        <View style={styles.skeletonMenuList}>
          {[0, 1, 2, 3].map(index => (
            <View key={index} style={styles.skeletonMenuRow}>
              <SkeletonBlock style={styles.skeletonMenuIcon} />
              <SkeletonBlock style={styles.skeletonMenuText} />
            </View>
          ))}
        </View>
      </View>
    </SkeletonGroup>
  );
}

type MenuRowProps = {
  icon: React.ComponentType<SvgProps>;
  label: string;
  onPress: () => void;
};

function MenuRow({ icon: Icon, label, onPress }: MenuRowProps) {
  return (
    <Pressable
      accessibilityLabel={label}
      accessibilityRole="button"
      onPress={onPress}
      style={({ pressed }) => [styles.menuRow, pressed && styles.pressed]}
    >
      <View style={styles.menuIconWrap}>
        <Icon height={20} width={20} />
      </View>
      <Text style={styles.menuText}>{label}</Text>
      <ChevronRightIcon height={24} width={24} />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  container: { backgroundColor: colors.gray10, flex: 1 },
  header: {
    alignItems: 'center',
    backgroundColor: colors.white,
    height: 56,
    justifyContent: 'center',
  },
  headerTitle: { ...typography.titleBold18, color: colors.black },
  settingsButton: { position: 'absolute', right: 20 },
  profileArea: { alignItems: 'center', height: 145, paddingTop: 23 },
  profileImage: { borderRadius: radius.pill, height: 70, width: 70 },
  nicknameButton: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: 4,
    marginTop: 12,
    minHeight: 24,
  },
  nickname: { ...typography.bodySemiBold16, color: colors.gray600 },
  disabledProfile: { opacity: 0.55 },
  suspendedText: { ...typography.captionRegular10, color: colors.danger, marginTop: 4 },
  sheet: {
    backgroundColor: colors.white,
    borderTopLeftRadius: 30,
    borderTopRightRadius: 30,
    flex: 1,
    paddingHorizontal: 27,
    paddingTop: 25,
  },
  shortcuts: { flexDirection: 'row', gap: 12 },
  shortcut: {
    alignItems: 'flex-start',
    borderRadius: 24,
    borderWidth: 1,
    flex: 1,
    minWidth: 0,
    paddingHorizontal: 16,
    paddingTop: 16,
    paddingBottom: 18,
    boxShadow: '0 4px 14px rgba(31, 65, 54, 0.05)',
  },
  petShortcut: { backgroundColor: '#F0F8F6', borderColor: '#DFEEE9' },
  adoptionShortcut: { backgroundColor: '#F5F8EE', borderColor: '#E6EDD9' },
  shortcutArtwork: { width: 64, height: 64, alignItems: 'center', justifyContent: 'center' },
  artworkHalo: {
    position: 'absolute',
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: '#DDEFEA',
  },
  adoptionHalo: { backgroundColor: '#E5EDD3' },
  shortcutText: { ...typography.bodySemiBold16, color: colors.gray800, marginTop: 14 },
  shortcutDescription: {
    ...typography.captionRegular10,
    fontSize: 11,
    lineHeight: 16,
    color: colors.gray600,
    marginTop: 5,
  },
  shortcutImage: { width: 64, height: 64, resizeMode: 'contain' },
  shortcutArrow: {
    position: 'absolute',
    top: 16,
    right: 14,
    width: 26,
    height: 26,
    borderRadius: 13,
    backgroundColor: 'rgba(255,255,255,0.85)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  shortcutPressed: { transform: [{ scale: 0.97 }], opacity: 0.85 },
  menuList: { marginTop: 25 },
  menuRow: { alignItems: 'center', flexDirection: 'row', height: 35 },
  menuIconWrap: { alignItems: 'center', height: 24, justifyContent: 'center', width: 24 },
  menuText: {
    ...typography.bodyRegular14,
    fontFamily: 'Pretendard-SemiBold',
    color: colors.gray700,
    flex: 1,
    marginLeft: 9,
  },
  pressed: { opacity: 0.65 },
  loadState: {
    alignItems: 'center',
    flex: 1,
    justifyContent: 'center',
    paddingHorizontal: 32,
  },
  loadStateTitle: { ...typography.titleBold18, color: colors.gray800 },
  loadStateDescription: {
    ...typography.bodyRegular14,
    color: colors.gray500,
    marginTop: 8,
    textAlign: 'center',
  },
  retryButton: {
    backgroundColor: colors.green400,
    borderRadius: radius.md,
    marginTop: 20,
    paddingHorizontal: 24,
    paddingVertical: 12,
  },
  retryButtonText: { ...typography.bodySemiBold16, color: colors.white },
  skeletonContainer: { backgroundColor: colors.gray10, flex: 1 },
  skeletonHeader: {
    alignItems: 'center',
    backgroundColor: colors.white,
    height: 56,
    justifyContent: 'center',
  },
  skeletonHeaderTitle: { height: 22, width: 82 },
  skeletonSettings: { height: 24, position: 'absolute', right: 20, width: 24 },
  skeletonProfile: { alignItems: 'center', height: 145, paddingTop: 23 },
  skeletonAvatar: { borderRadius: 35, height: 70, width: 70 },
  skeletonNickname: { height: 18, marginTop: 12, width: 92 },
  skeletonSheet: {
    backgroundColor: colors.white,
    borderTopLeftRadius: 30,
    borderTopRightRadius: 30,
    flex: 1,
    paddingHorizontal: 27,
    paddingTop: 25,
  },
  skeletonShortcuts: { flexDirection: 'row', gap: 12 },
  skeletonShortcut: {
    borderColor: colors.gray200,
    borderRadius: 24,
    borderWidth: 1,
    flex: 1,
    height: 158,
    padding: 16,
  },
  skeletonArtwork: { borderRadius: 28, height: 56, width: 56 },
  skeletonShortcutTitle: { height: 17, marginTop: 14, width: '62%' },
  skeletonShortcutDescription: { height: 12, marginTop: 7, width: '82%' },
  skeletonMenuList: { gap: 14, marginTop: 25 },
  skeletonMenuRow: { alignItems: 'center', flexDirection: 'row', height: 35 },
  skeletonMenuIcon: { height: 22, width: 22 },
  skeletonMenuText: { height: 15, marginLeft: 10, width: '42%' },
});
