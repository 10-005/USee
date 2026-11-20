import { Image, Pressable, StyleSheet, Text, View, type ImageSourcePropType } from 'react-native';

import { colors, radius, spacing, typography } from '../../../theme';

type AnimalCardProps = {
  date: string;
  imageSource: ImageSourcePropType;
  location: string;
  name: string;
  onPress: () => void;
  status: string;
};

export function AnimalCard({
  date,
  imageSource,
  location,
  name,
  onPress,
  status,
}: AnimalCardProps) {
  return (
    <Pressable
      accessibilityLabel={`${name}, ${status}, ${location}, ${date}`}
      accessibilityRole="button"
      onPress={onPress}
      style={({ pressed }) => [styles.container, pressed && styles.pressed]}
    >
      <Image source={imageSource} style={styles.image} />
      <View style={styles.content}>
        <View style={styles.badge}>
          <Text style={styles.badgeLabel}>{status}</Text>
        </View>
        <Text numberOfLines={1} style={styles.name}>
          {name}
        </Text>
        <Text numberOfLines={1} style={styles.metadata}>
          {location}
        </Text>
        <Text style={styles.metadata}>{date}</Text>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  container: {
    width: 180,
  },
  image: {
    backgroundColor: colors.gray200,
    borderRadius: radius.sm,
    height: 180,
    width: 180,
  },
  content: {
    alignItems: 'flex-start',
    gap: spacing[2],
    paddingTop: spacing[4],
  },
  badge: {
    backgroundColor: colors.blue,
    borderRadius: radius.xs,
    paddingHorizontal: spacing[3],
    paddingVertical: spacing[1],
  },
  badgeLabel: {
    ...typography.captionSemiBold12,
    color: colors.white,
  },
  name: {
    ...typography.titleBold18,
    color: colors.gray700,
    width: '100%',
  },
  metadata: {
    ...typography.bodyRegular14,
    color: colors.gray400,
    width: '100%',
  },
  pressed: {
    opacity: 0.75,
  },
});
