import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useMemo, useState } from 'react';
import {
  FlatList,
  Image,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';

import type { RootStackParamList } from '../../app/navigation/types';
import { BottomNavigation } from '../../components/common';
import { colors, radius, typography } from '../../theme';
import { adoptionAnimals, type AdoptionAnimal } from '../adoption/data';

type Props = NativeStackScreenProps<RootStackParamList, 'Home'>;
const filters = ['기간', '지역', '축종', '상태', '성별', '중성화여부', '최신순'];
const adoptIcon = () => <Text>♧</Text>;
const findIcon = () => <Text>⌕</Text>;
const chatIcon = () => <Text>◯</Text>;
const myIcon = () => <Text>♙</Text>;
const navigationItems = [
  { key: 'adopt', label: '입양', icon: adoptIcon },
  { key: 'find', label: '찾아요', icon: findIcon },
  { key: 'chat', label: '채팅', icon: chatIcon },
  { key: 'my', label: '마이페이지', icon: myIcon },
] as const;

export function HomeScreen({ navigation }: Props) {
  const insets = useSafeAreaInsets();
  const [query, setQuery] = useState('');
  const animals = useMemo(
    () =>
      adoptionAnimals.filter(item =>
        `${item.name} ${item.location} ${item.summary}`.toLowerCase().includes(query.toLowerCase()),
      ),
    [query],
  );

  const renderAnimal = ({ item }: { item: AdoptionAnimal }) => (
    <Pressable
      accessibilityRole="button"
      onPress={() => navigation.navigate('AdoptionDetail', { animalId: item.id })}
      style={({ pressed }) => [styles.card, pressed && styles.pressed]}
    >
      <Image resizeMode="cover" source={item.image} style={styles.cardImage} />
      <View style={[styles.statusBadge, item.status === '종료' && styles.endedBadge]}>
        <Text style={styles.statusText}>{item.status}</Text>
      </View>
      <Text numberOfLines={1} style={styles.cardTitle}>
        {item.name}
      </Text>
      <Text numberOfLines={1} style={styles.cardMeta}>
        ⌖ {item.location}
      </Text>
      <Text style={styles.cardMeta}>▣ {item.date}</Text>
      <Text numberOfLines={1} style={styles.cardSummary}>
        {item.summary}
      </Text>
      <Text numberOfLines={1} style={styles.cardDescription}>
        {item.description}
      </Text>
    </Pressable>
  );

  return (
    <SafeAreaView edges={['top']} style={styles.safeArea}>
      <View style={styles.header}>
        <Image
          resizeMode="contain"
          source={require('../../assets/images/logo.png')}
          style={styles.logo}
        />
        <Text accessibilityLabel="알림" style={styles.headerIcon}>
          ♧
        </Text>
      </View>
      <View style={styles.searchBox}>
        <Text style={styles.searchIcon}>⌕</Text>
        <TextInput
          onChangeText={setQuery}
          placeholder="특징으로 검색해보세요"
          placeholderTextColor={colors.gray400}
          style={styles.searchInput}
          value={query}
        />
      </View>
      <ScrollView
        contentContainerStyle={styles.filters}
        horizontal
        showsHorizontalScrollIndicator={false}
      >
        {filters.map(filter => (
          <Pressable key={filter} style={styles.filterChip}>
            <Text style={styles.filterText}>{filter}⌄</Text>
          </Pressable>
        ))}
      </ScrollView>
      <FlatList
        columnWrapperStyle={styles.column}
        contentContainerStyle={[styles.listContent, { paddingBottom: 88 + insets.bottom }]}
        data={animals}
        keyExtractor={item => item.id}
        numColumns={2}
        renderItem={renderAnimal}
        showsVerticalScrollIndicator={false}
      />
      <View style={[styles.navigationWrap, { bottom: Math.max(insets.bottom, 12) }]}>
        <BottomNavigation
          centerAction={{
            accessibilityLabel: '등록',
            icon: <Text style={styles.plus}>＋</Text>,
            onPress: () => undefined,
          }}
          items={navigationItems}
          onSelect={() => undefined}
          selectedKey="adopt"
        />
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: { backgroundColor: colors.white, flex: 1 },
  header: { alignItems: 'center', flexDirection: 'row', height: 56, justifyContent: 'center' },
  logo: { height: 34, width: 88 },
  headerIcon: { color: colors.black, fontSize: 24, position: 'absolute', right: 20 },
  searchBox: {
    alignItems: 'center',
    borderColor: colors.gray200,
    borderRadius: radius.md,
    borderWidth: 1,
    flexDirection: 'row',
    height: 50,
    marginHorizontal: 20,
    paddingHorizontal: 14,
  },
  searchIcon: { color: colors.green100, fontSize: 28, marginRight: 4 },
  searchInput: { ...typography.bodyRegular16, color: colors.gray700, flex: 1, paddingVertical: 0 },
  filters: { gap: 6, paddingHorizontal: 20, paddingVertical: 12 },
  filterChip: {
    borderColor: colors.gray200,
    borderRadius: radius.pill,
    borderWidth: 1,
    height: 28,
    justifyContent: 'center',
    paddingHorizontal: 10,
  },
  filterText: { ...typography.bodyRegular14, color: colors.gray500 },
  listContent: { paddingHorizontal: 20 },
  column: { gap: 12 },
  card: { flex: 1, height: 316, marginBottom: 18, minWidth: 0 },
  cardImage: { aspectRatio: 1, borderRadius: radius.sm, width: '100%' },
  statusBadge: {
    alignSelf: 'flex-start',
    backgroundColor: colors.green100,
    borderBottomLeftRadius: radius.xs,
    borderTopRightRadius: radius.xs,
    marginTop: -24,
    paddingHorizontal: 10,
    paddingVertical: 4,
  },
  endedBadge: { backgroundColor: colors.gray400 },
  statusText: { ...typography.captionSemiBold12, color: colors.white },
  cardTitle: { ...typography.titleBold18, color: colors.black, marginTop: 8 },
  cardMeta: { ...typography.bodyRegular14, color: colors.gray700, marginTop: 3 },
  cardSummary: { ...typography.bodyRegular14, color: colors.gray500, marginTop: 3 },
  cardDescription: { ...typography.bodyRegular14, color: colors.gray500, marginTop: 3 },
  navigationWrap: { left: 25, position: 'absolute', right: 25 },
  plus: { color: colors.white, fontSize: 28 },
  pressed: { opacity: 0.75 },
});
