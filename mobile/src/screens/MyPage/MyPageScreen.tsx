import { Image, Pressable, StyleSheet, Text, View } from 'react-native';
import type { SvgProps } from 'react-native-svg';

import ApplicationListIcon from '../../assets/icons/mypage/application-list.svg';
import ChevronRightIcon from '../../assets/icons/mypage/chevron-right.svg';
import MyPostsIcon from '../../assets/icons/mypage/my-posts.svg';
import NoticeIcon from '../../assets/icons/mypage/notice.svg';
import PencilIcon from '../../assets/icons/mypage/pencil.svg';
import SavedPostsIcon from '../../assets/icons/mypage/saved-posts.svg';
import SettingsIcon from '../../assets/icons/mypage/settings.svg';
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
          onPress={onOpenProfileEdit}
          style={({ pressed }) => [styles.nicknameButton, pressed && styles.pressed]}
        >
          <Text style={styles.nickname}>{profile.nickname}</Text>
          <PencilIcon />
        </Pressable>
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
});
