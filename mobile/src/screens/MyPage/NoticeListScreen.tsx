import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { FlatList, Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import type { RootStackParamList } from '../../app/navigation/types';
import LeftArrowIcon from '../../assets/icons/common/left-arrow.svg';
import ChevronRightIcon from '../../assets/icons/mypage/chevron-right.svg';
import { colors, typography } from '../../theme';
import { notices, type Notice } from './noticeData';

type Props = NativeStackScreenProps<RootStackParamList, 'NoticeList'>;

export function NoticeListScreen({ navigation }: Props) {
  const renderNotice = ({ item }: { item: Notice }) => (
    <Pressable
      accessibilityLabel={`${item.title} 상세보기`}
      accessibilityRole="button"
      onPress={() => navigation.navigate('NoticeDetail', { noticeId: item.id })}
      style={({ pressed }) => [styles.noticeRow, pressed && styles.pressed]}
    >
      <View style={styles.noticeText}>
        <Text numberOfLines={1} style={styles.noticeTitle}>
          {item.title}
        </Text>
        <Text style={styles.noticeDate}>{item.date}</Text>
      </View>
      <ChevronRightIcon height={24} width={24} />
    </Pressable>
  );

  return (
    <SafeAreaView style={styles.safeArea}>
      <NoticeHeader onBack={navigation.goBack} />
      {notices.length ? (
        <FlatList
          data={notices}
          keyExtractor={item => item.id}
          renderItem={renderNotice}
          showsVerticalScrollIndicator={false}
        />
      ) : (
        <View style={styles.emptyState}>
          <Text style={styles.emptyTitle}>등록된 공지사항이 없습니다</Text>
          <Text style={styles.emptyDescription}>
            새로운 소식이 등록되면 이곳에서 안내해 드릴게요
          </Text>
        </View>
      )}
    </SafeAreaView>
  );
}

export function NoticeHeader({ onBack }: { onBack: () => void }) {
  return (
    <View style={styles.header}>
      <Pressable
        accessibilityLabel="뒤로가기"
        hitSlop={10}
        onPress={onBack}
        style={styles.backButton}
      >
        <LeftArrowIcon height={24} width={24} />
      </Pressable>
      <Text style={styles.headerTitle}>공지사항</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  safeArea: { backgroundColor: colors.white, flex: 1 },
  header: { alignItems: 'center', height: 56, justifyContent: 'center' },
  backButton: { left: 18, position: 'absolute' },
  headerTitle: { ...typography.titleSemiBold18, color: colors.black },
  noticeRow: {
    alignItems: 'center',
    borderBottomColor: '#ECECE8',
    borderBottomWidth: 1,
    flexDirection: 'row',
    height: 66,
    paddingHorizontal: 26,
  },
  noticeText: { flex: 1 },
  noticeTitle: {
    ...typography.bodyRegular14,
    color: colors.gray600,
    fontFamily: 'Pretendard-SemiBold',
  },
  noticeDate: {
    color: colors.gray600,
    fontFamily: 'Pretendard-Regular',
    fontSize: 12,
    lineHeight: 15,
    marginTop: 5,
  },
  emptyState: {
    alignItems: 'center',
    justifyContent: 'center',
    left: 0,
    position: 'absolute',
    right: 0,
    top: '44%',
  },
  emptyTitle: {
    ...typography.bodySemiBold16,
    color: colors.gray500,
    fontFamily: 'Pretendard-Bold',
  },
  emptyDescription: {
    color: colors.gray500,
    fontFamily: 'Pretendard-Regular',
    fontSize: 12,
    lineHeight: 22,
    marginTop: 8,
  },
  pressed: { opacity: 0.65 },
});
