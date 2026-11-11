import { memo, useState, type ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';

type Props = { active: boolean; children: ReactNode };

// Mount on first visit, then keep native views and local state across tab switches.
export const RetainedTab = memo(
  function RetainedTabContent({ active, children }: Props) {
    const [visited, setVisited] = useState(active);
    if (active && !visited) {
      setVisited(true);
    }
    if (!active && !visited) {
      return null;
    }
    return (
      <View
        accessibilityElementsHidden={!active}
        importantForAccessibility={active ? 'auto' : 'no-hide-descendants'}
        pointerEvents={active ? 'auto' : 'none'}
        style={active ? styles.visible : styles.hidden}
      >
        {children}
      </View>
    );
  },
  (previous, next) => !previous.active && !next.active,
);

const styles = StyleSheet.create({
  visible: { flex: 1 },
  hidden: { display: 'none' },
});
