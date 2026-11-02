import React from 'react';
import { Text } from 'react-native';
import ReactTestRenderer from 'react-test-renderer';

import { BottomNavigation } from '..';

const items = [
  { key: 'home', label: '홈', icon: () => <Text>H</Text> },
  { key: 'chat', label: '채팅', icon: () => <Text>C</Text> },
] as const;

describe('BottomNavigation', () => {
  it('reports selection and actions', async () => {
    const onSelect = jest.fn();
    const onCenterPress = jest.fn();
    let renderer: ReactTestRenderer.ReactTestRenderer;

    await ReactTestRenderer.act(() => {
      renderer = ReactTestRenderer.create(
        <BottomNavigation
          centerAction={{
            accessibilityLabel: '동물 등록',
            icon: <Text>+</Text>,
            onPress: onCenterPress,
          }}
          items={items}
          onSelect={onSelect}
          selectedKey="home"
        />,
      );
    });

    const homeTab = renderer!.root.findByProps({ accessibilityLabel: '홈' });
    expect(homeTab.props.accessibilityState).toEqual({ selected: true });

    renderer!.root.findByProps({ accessibilityLabel: '채팅' }).props.onPress();
    renderer!.root.findByProps({ accessibilityLabel: '동물 등록' }).props.onPress();

    expect(onSelect).toHaveBeenCalledWith('chat');
    expect(onCenterPress).toHaveBeenCalledTimes(1);
  });
});
