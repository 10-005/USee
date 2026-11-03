import { Alert, Image, Pressable, StyleSheet, Text, View } from 'react-native';
import type { SvgProps } from 'react-native-svg';

import ApplicationListIcon from '../../assets/icons/mypage/application-list.svg';
import ChevronRightIcon from '../../assets/icons/mypage/chevron-right.svg';
import MockResultIcon from '../../assets/icons/mypage/mock-result.svg';
import MyPostsIcon from '../../assets/icons/mypage/my-posts.svg';
import NoticeIcon from '../../assets/icons/mypage/notice.svg';
import PencilIcon from '../../assets/icons/mypage/pencil.svg';
import PetRegisterIcon from '../../assets/icons/mypage/pet-register.svg';
import SavedPostsIcon from '../../assets/icons/mypage/saved-posts.svg';
import SettingsIcon from '../../assets/icons/mypage/settings.svg';
import { useProfileStore } from '../../stores/profileStore';
import { colors, radius, typography } from '../../theme';

type Props = {
  onOpenMockAdoption: () => void;
  onOpenAdoptionApplications: () => void;
  onOpenPets: () => void;
  onOpenProfileEdit: () => void;
};

const unavailable = (name: string) =>
  Alert.alert(name, '해당 화면은 다음 작업에서 연결될 예정입니다.');

const menuItems = [
  { label: '내가 작성한 게시글', icon: MyPostsIcon },
  { label: '저장한 게시글', icon: SavedPostsIcon },
  { label: '공지사항', icon: NoticeIcon },
] as const;

export function MyPageScreen({
  onOpenAdoptionApplications,
  onOpenMockAdoption,
  onOpenPets,
  onOpenProfileEdit,
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
          onPress={() => unavailable('환경설정')}
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
            style={({ pressed }) => [styles.shortcut, pressed && styles.pressed]}
          >
            <PetRegisterIcon />
            <Text style={styles.shortcutText}>반려동물 등록</Text>
          </Pressable>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="모의입양"
            onPress={onOpenMockAdoption}
            style={({ pressed }) => [styles.shortcut, pressed && styles.pressed]}
          >
            <MockResultIcon />
            <Text style={styles.shortcutText}>모의입양</Text>
          </Pressable>
        </View>

        <View style={styles.menuList}>
          <MenuRow
            icon={ApplicationListIcon}
            label="입양 신청"
            onPress={onOpenAdoptionApplications}
          />
          {menuItems.map(item => (
            <MenuRow
              icon={item.icon}
              key={item.label}
              label={item.label}
              onPress={() => unavailable(item.label)}
            />
          ))}
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
  shortcuts: { flexDirection: 'row', gap: 20 },
  shortcut: {
    alignItems: 'center',
    backgroundColor: '#EAF4E9',
    borderRadius: 15,
    flex: 1,
    height: 85,
    justifyContent: 'center',
  },
  shortcutText: { ...typography.bodyRegular14, color: colors.gray800, marginTop: 10 },
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
