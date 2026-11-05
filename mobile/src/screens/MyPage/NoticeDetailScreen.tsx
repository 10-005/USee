import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import type { RootStackParamList } from '../../app/navigation/types';
import { colors, typography } from '../../theme';
import { NoticeHeader } from './NoticeListScreen';
import { notices } from './noticeData';

type Props = NativeStackScreenProps<RootStackParamList, 'NoticeDetail'>;

export function NoticeDetailScreen({ navigation, route }: Props) {
  const notice = notices.find(item => item.id === route.params.noticeId) ?? notices[0];

  return (
    <SafeAreaView style={styles.safeArea}>
      <NoticeHeader onBack={navigation.goBack} />
      <ScrollView
        contentContainerStyle={styles.content}
        decelerationRate="fast"
        showsVerticalScrollIndicator={false}
      >
        <Text style={styles.title}>{notice.title}</Text>
        <Text style={styles.date}>{notice.date}</Text>
        <View style={styles.divider} />
        <Text style={styles.body}>{notice.content}</Text>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: { backgroundColor: colors.white, flex: 1 },
  content: { paddingBottom: 40, paddingHorizontal: 26, paddingTop: 17 },
  title: { ...typography.bodySemiBold16, color: colors.black },
  date: {
    color: colors.gray600,
    fontFamily: 'Pretendard-Regular',
    fontSize: 12,
    lineHeight: 15,
    marginTop: 10,
  },
  divider: { backgroundColor: '#ECECE8', height: 1, marginTop: 22 },
  body: {
    color: colors.gray600,
    fontFamily: 'Pretendard-Regular',
    fontSize: 12,
    lineHeight: 15,
    marginTop: 21,
  },
});
