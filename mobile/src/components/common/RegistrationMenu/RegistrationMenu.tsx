import { Pressable, StyleSheet, Text, View } from 'react-native';

import FoundReportIcon from '../../../assets/icons/common/found-report.svg';
import LostReportIcon from '../../../assets/icons/common/lost-report.svg';
import { colors, radius, typography } from '../../../theme';

type RegistrationMenuProps = {
  bottom: number;
  onClose: () => void;
  onSelectFound: () => void;
  onSelectLost: () => void;
};

export function RegistrationMenu({
  bottom,
  onClose,
  onSelectFound,
  onSelectLost,
}: RegistrationMenuProps) {
  return (
    <View style={StyleSheet.absoluteFill}>
      <Pressable
        accessibilityLabel="등록 메뉴 닫기"
        accessibilityRole="button"
        onPress={onClose}
        style={styles.backdrop}
      />
      <View style={[styles.menu, { bottom }]}>
        <Pressable
          accessibilityRole="button"
          onPress={onSelectLost}
          style={({ pressed }) => [styles.menuItem, pressed && styles.pressed]}
        >
          <LostReportIcon height={24} width={24} />
          <Text style={styles.label}>잃어버렸어요</Text>
        </Pressable>
        <Pressable
          accessibilityRole="button"
          onPress={onSelectFound}
          style={({ pressed }) => [styles.menuItem, pressed && styles.pressed]}
        >
          <FoundReportIcon height={24} width={24} />
          <Text style={styles.label}>목격했어요</Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    backgroundColor: 'transparent',
    bottom: 0,
    left: 0,
    position: 'absolute',
    right: 0,
    top: 0,
  },
  menu: {
    alignSelf: 'center',
    backgroundColor: colors.white,
    borderColor: colors.gray200,
    borderRadius: radius.md,
    borderWidth: 1,
    paddingHorizontal: 14,
    paddingVertical: 8,
    position: 'absolute',
    shadowColor: colors.black,
    shadowOffset: { height: 2, width: 0 },
    shadowOpacity: 0.12,
    shadowRadius: 6,
    width: 160,
    elevation: 6,
    left: '50%',
    marginLeft: -80,
  },
  menuItem: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: 10,
    height: 40,
  },
  label: {
    ...typography.bodySemiBold16,
    color: '#FA705B',
  },
  pressed: { opacity: 0.65 },
});
