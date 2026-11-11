import { Fragment, useRef, useState, type ReactNode } from 'react';
import {
  Platform,
  requireNativeComponent,
  ScrollView,
  StyleSheet,
  View,
  type StyleProp,
  type ViewProps,
  type ViewStyle,
} from 'react-native';

const PostTabPage =
  Platform.OS === 'android' ? requireNativeComponent<ViewProps>('PostTabPage') : View;

type Props<T extends string> = {
  tabs: ReadonlyArray<{ key: T; label: string }>;
  renderTab: (label: string, selected: boolean, select: () => void) => ReactNode;
  renderPage: (key: T) => ReactNode;
  tabStyle: StyleProp<ViewStyle>;
};

export function PostTabPager<T extends string>({
  tabs,
  renderTab,
  renderPage,
  tabStyle,
}: Props<T>) {
  const pager = useRef<ScrollView>(null);
  const [width, setWidth] = useState(0);
  const [index, setIndex] = useState(0);
  return (
    <>
      <View accessibilityRole="tablist" style={tabStyle}>
        {tabs.map((tab, i) => (
          <Fragment key={tab.key}>
            {renderTab(tab.label, index === i, () => {
              setIndex(i);
              pager.current?.scrollTo({ x: i * width, animated: true });
            })}
          </Fragment>
        ))}
      </View>
      <View style={styles.pages} onLayout={event => setWidth(event.nativeEvent.layout.width)}>
        {width > 0 && (
          <ScrollView
            ref={pager}
            horizontal
            pagingEnabled
            directionalLockEnabled
            showsHorizontalScrollIndicator={false}
            onContentSizeChange={() =>
              pager.current?.scrollTo({ x: index * width, animated: false })
            }
            onMomentumScrollEnd={event =>
              setIndex(
                Math.max(
                  0,
                  Math.min(tabs.length - 1, Math.round(event.nativeEvent.contentOffset.x / width)),
                ),
              )
            }
          >
            {tabs.map((tab, i) => (
              <PostTabPage
                key={tab.key}
                style={{ width }}
                importantForAccessibility={index === i ? 'auto' : 'no-hide-descendants'}
                accessibilityElementsHidden={index !== i}
              >
                {renderPage(tab.key)}
              </PostTabPage>
            ))}
          </ScrollView>
        )}
      </View>
    </>
  );
}

const styles = StyleSheet.create({ pages: { flex: 1 } });
