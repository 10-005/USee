import {
  createContext,
  useContext,
  useMemo,
  useRef,
  useState,
  type ComponentRef,
  type ReactNode,
  type RefObject,
} from 'react';
import {
  FlatList,
  Modal,
  PixelRatio,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { colors, radius, typography } from '../../theme';

type Options = {
  fieldId: string;
  first: number;
  last: number;
  current: number;
  unit: string;
  select: (value: number) => void;
};
type OpenMenu = (anchor: RefObject<ComponentRef<typeof View> | null>, options: Options) => void;
const CalendarMenu = createContext<OpenMenu>(() => {});
export const CalendarOpenField = createContext<string | undefined>(undefined);
export const useCalendarMenu = () => useContext(CalendarMenu);
const rowHeight = PixelRatio.roundToNearestPixel(52);
const frameBorder = PixelRatio.roundToNearestPixel(1);

export function CalendarDialog({
  visible,
  title,
  onClose,
  children,
}: {
  visible: boolean;
  title: string;
  onClose: () => void;
  children: ReactNode;
}) {
  const insets = useSafeAreaInsets();
  const card = useRef<ComponentRef<typeof View>>(null);
  const [menu, setMenu] = useState<
    (Options & { top: number; height: number; paddingRows: number }) | null
  >(null);
  const [focus, setFocus] = useState(0);
  const centered = useRef(0);
  const wheel = useMemo(() => {
    if (!menu) {
      return { items: [], offsets: [] };
    }
    const count = menu.last - menu.first + 1;
    return {
      items: Array.from(
        { length: count + 2 * menu.paddingRows },
        (_, i) => menu.first + i - menu.paddingRows,
      ),
      offsets: Array.from({ length: count }, (_, i) => i * rowHeight),
    };
  }, [menu]);
  const openMenu: OpenMenu = (anchor, options) => {
    const display = (bottom: number, available: number) => {
      const fit = Math.min(
        5,
        Math.floor((available - 2 * frameBorder) / rowHeight),
      );
      const paddingRows = Math.max(0, Math.floor((fit - 1) / 2));
      const height = (2 * paddingRows + 1) * rowHeight + 2 * frameBorder;
      centered.current = options.current;
      setFocus(options.current);
      setMenu({
        ...options,
        height,
        paddingRows,
        top: Math.max(0, Math.min(bottom, available - height)),
      });
    };
    const anchorRect = anchor.current?.getBoundingClientRect?.();
    const cardRect = card.current?.getBoundingClientRect?.();
    display(
      anchorRect && cardRect ? anchorRect.bottom - cardRect.top : 0,
      cardRect?.height || rowHeight * 5 + 2 * frameBorder,
    );
  };
  const close = () => (menu ? setMenu(null) : onClose());
  const apply = (value: number) => {
    menu?.select(value);
    setMenu(null);
  };
  const dismissOutside = () => (menu ? apply(centered.current) : onClose());
  return (
    <Modal
      transparent
      statusBarTranslucent
      animationType="fade"
      visible={visible}
      onRequestClose={close}
    >
      <View
        style={[styles.overlay, { paddingTop: insets.top + 12, paddingBottom: insets.bottom + 12 }]}
      >
        <Pressable
          accessibilityLabel={title + ' 닫기'}
          onPress={dismissOutside}
          style={StyleSheet.absoluteFill}
        />
        <View ref={card} collapsable={false} accessibilityViewIsModal style={styles.card}>
          <ScrollView
            bounces={false}
            keyboardShouldPersistTaps="handled"
            contentContainerStyle={styles.content}
            importantForAccessibility={menu ? 'no-hide-descendants' : 'auto'}
            accessibilityElementsHidden={!!menu}
          >
            <Text style={styles.title}>{title}</Text>
            <CalendarOpenField.Provider value={menu?.fieldId}>
              <CalendarMenu.Provider value={openMenu}>{children}</CalendarMenu.Provider>
            </CalendarOpenField.Provider>
          </ScrollView>
          {menu && (
            <View style={styles.layer}>
              <Pressable
                accessibilityLabel="목록 닫기"
                style={StyleSheet.absoluteFill}
                onPress={dismissOutside}
              />
              <View style={[styles.listFrame, { top: menu.top, height: menu.height }]}>
                <FlatList
                  data={wheel.items}
                  keyExtractor={value => String(value)}
                  initialScrollIndex={menu.current - menu.first}
                  getItemLayout={(_, index) => ({
                    length: rowHeight,
                    offset: rowHeight * index,
                    index,
                  })}
                  showsVerticalScrollIndicator={false}
                  snapToOffsets={wheel.offsets}
                  disableIntervalMomentum
                  bounces={false}
                  overScrollMode="never"
                  decelerationRate="fast"
                  scrollEventThrottle={16}
                  onScroll={event => {
                    centered.current = Math.max(
                      menu.first,
                      Math.min(
                        menu.last,
                        menu.first + Math.round(event.nativeEvent.contentOffset.y / rowHeight),
                      ),
                    );
                    setFocus(centered.current);
                  }}
                  renderItem={({ item: value }) => {
                    if (value < menu.first || value > menu.last) {
                      return <View style={styles.option} />;
                    }
                    const distance = Math.abs(value - focus);
                    return (
                      <Pressable
                        key={value}
                        accessibilityRole="button"
                        accessibilityLabel={value + menu.unit}
                        accessibilityState={{ selected: value === focus }}
                        style={styles.option}
                        onPress={() => apply(value)}
                      >
                        <Text
                          style={[
                            styles.number,
                            distance === 1 && styles.neighbor,
                            distance > 1 && styles.distant,
                          ]}
                        >
                          {value}
                        </Text>
                      </Pressable>
                    );
                  }}
                />
              </View>
            </View>
          )}
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 18,
    backgroundColor: 'rgba(17,24,39,0.42)',
  },
  card: {
    width: '100%',
    maxWidth: 420,
    maxHeight: '100%',
    flexShrink: 1,
    backgroundColor: colors.white,
    borderRadius: radius.lg,
    overflow: 'hidden',
  },
  content: { paddingHorizontal: 26, paddingVertical: 22 },
  layer: { ...StyleSheet.absoluteFill, backgroundColor: 'rgba(0,0,0,0.30)' },
  listFrame: {
    position: 'absolute',
    left: 26,
    right: 26,
    backgroundColor: colors.white,
    borderRadius: 8,
    borderWidth: frameBorder,
    borderColor: colors.gray300,
    overflow: 'hidden',
  },
  option: {
    height: rowHeight,
    alignItems: 'center',
    justifyContent: 'center',
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderColor: colors.gray300,
  },
  number: { fontSize: 22, fontWeight: '700', color: '#231F20' },
  neighbor: { color: '#BAB8B0' },
  distant: { color: '#EBEAE5' },
  title: {
    ...typography.titleSemiBold18,
    color: colors.gray800,
    textAlign: 'center',
    marginBottom: 14,
  },
});
