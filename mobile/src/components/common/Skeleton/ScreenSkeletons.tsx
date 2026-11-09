import { StyleSheet, View } from 'react-native';

import { colors } from '../../../theme';
import { SkeletonBlock, SkeletonGroup } from './Skeleton';

export function ListScreenSkeleton({ accessibilityLabel }: { accessibilityLabel: string }) {
  return (
    <SkeletonGroup accessibilityLabel={accessibilityLabel} style={styles.listContainer}>
      <SkeletonBlock style={styles.search} />
      <View style={styles.chips}>
        <SkeletonBlock style={[styles.chip, styles.chipWide]} />
        <SkeletonBlock style={styles.chip} />
        <SkeletonBlock style={styles.chip} />
      </View>
      {[0, 1, 2, 3].map(index => (
        <View key={index} style={styles.listCard}>
          <SkeletonBlock style={styles.thumbnail} />
          <View style={styles.cardCopy}>
            <SkeletonBlock style={styles.title} />
            <SkeletonBlock style={styles.line} />
            <SkeletonBlock style={styles.shortLine} />
          </View>
        </View>
      ))}
    </SkeletonGroup>
  );
}

export function DetailScreenSkeleton({ accessibilityLabel }: { accessibilityLabel: string }) {
  return (
    <SkeletonGroup accessibilityLabel={accessibilityLabel} style={styles.detailContainer}>
      <View style={styles.profileRow}>
        <SkeletonBlock style={styles.avatar} />
        <View style={styles.profileCopy}>
          <SkeletonBlock style={styles.profileName} />
          <SkeletonBlock style={styles.profileMeta} />
        </View>
      </View>
      <SkeletonBlock style={styles.hero} />
      <SkeletonBlock style={styles.detailTitle} />
      {[0, 1, 2].map(index => (
        <View key={index} style={styles.section}>
          <SkeletonBlock style={styles.sectionTitle} />
          <SkeletonBlock style={styles.sectionLine} />
          <SkeletonBlock style={styles.sectionLine} />
          <SkeletonBlock style={styles.sectionShortLine} />
        </View>
      ))}
    </SkeletonGroup>
  );
}

export function ProfileScreenSkeleton({ accessibilityLabel }: { accessibilityLabel: string }) {
  return (
    <SkeletonGroup accessibilityLabel={accessibilityLabel} style={styles.profileContainer}>
      <SkeletonBlock style={styles.profileCover} />
      <SkeletonBlock style={styles.largeAvatar} />
      <SkeletonBlock style={styles.profileHeading} />
      {[0, 1, 2, 3].map(index => (
        <View key={index} style={styles.infoRow}>
          <SkeletonBlock style={styles.infoLabel} />
          <SkeletonBlock style={styles.infoValue} />
        </View>
      ))}
      <SkeletonBlock style={styles.profileBodyTitle} />
      <SkeletonBlock style={styles.profileBodyLine} />
      <SkeletonBlock style={styles.profileBodyLine} />
      <SkeletonBlock style={styles.profileBodyShortLine} />
    </SkeletonGroup>
  );
}

const styles = StyleSheet.create({
  avatar: { borderRadius: 22, height: 44, width: 44 },
  cardCopy: { flex: 1, gap: 10 },
  chip: { borderRadius: 14, height: 28, width: 62 },
  chipWide: { width: 82 },
  chips: { flexDirection: 'row', gap: 8 },
  detailContainer: { flex: 1 },
  detailTitle: { height: 22, margin: 20, width: '55%' },
  hero: { aspectRatio: 1, width: '100%' },
  infoLabel: { height: 16, width: 72 },
  infoRow: { flexDirection: 'row', gap: 24 },
  infoValue: { flex: 1, height: 16 },
  largeAvatar: { borderRadius: 38, height: 76, marginTop: -38, width: 76 },
  line: { height: 14, width: '88%' },
  listCard: {
    alignItems: 'center',
    borderBottomColor: colors.gray200,
    borderBottomWidth: StyleSheet.hairlineWidth,
    flexDirection: 'row',
    gap: 14,
    paddingVertical: 14,
  },
  listContainer: { flex: 1, paddingHorizontal: 20, paddingTop: 12 },
  profileBodyLine: { height: 15, marginTop: 10, width: '100%' },
  profileBodyShortLine: { height: 15, marginTop: 10, width: '66%' },
  profileBodyTitle: { height: 19, marginTop: 30, width: 110 },
  profileContainer: { flex: 1, paddingHorizontal: 24 },
  profileCopy: { flex: 1, gap: 7 },
  profileCover: { height: 220, marginHorizontal: -24 },
  profileHeading: { height: 24, marginTop: 18, width: '54%' },
  profileMeta: { height: 11, width: '38%' },
  profileName: { height: 16, width: '46%' },
  profileRow: { alignItems: 'center', flexDirection: 'row', gap: 12, padding: 18 },
  search: { height: 46, marginBottom: 14, width: '100%' },
  section: {
    borderTopColor: colors.gray200,
    borderTopWidth: 1,
    gap: 12,
    padding: 20,
  },
  sectionLine: { height: 14, width: '100%' },
  sectionShortLine: { height: 14, width: '62%' },
  sectionTitle: { height: 19, marginBottom: 4, width: 92 },
  shortLine: { height: 14, width: '58%' },
  thumbnail: { borderRadius: 12, height: 82, width: 82 },
  title: { height: 18, width: '64%' },
});
