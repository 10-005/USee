import React from 'react';
import { Animated, Platform, Text } from 'react-native';
import ReactTestRenderer from 'react-test-renderer';

import { BottomNavigation } from '..';

const items = [
  { key: 'home', label: '홈', icon: () => <Text>H</Text> },
  { key: 'chat', label: '채팅', icon: () => <Text>C</Text> },
] as const;

describe('BottomNavigation', () => {
  it('pauses Android blur during a transition and reuses the native animation graph', async () => {
    const platform = jest.spyOn(Platform, 'select').mockImplementation(spec => spec.android);
    const completions: Array<(result: { finished: boolean }) => void> = [];
    const motion = jest.spyOn(Animated, 'parallel').mockImplementation(() => ({
      start: callback => {
        if (callback) {
          completions.push(callback);
        }
      },
      stop: jest.fn(),
      reset: jest.fn(),
    }));
    const graph = jest.spyOn(Animated, 'divide');
    let renderer!: ReactTestRenderer.ReactTestRenderer;
    const onSelect = jest.fn();
    const render = (selectedKey: 'home' | 'chat') => (
      <BottomNavigation items={items} selectedKey={selectedKey} onSelect={onSelect} />
    );
    try {
      await ReactTestRenderer.act(() => {
        renderer = ReactTestRenderer.create(render('home'));
      });
      await ReactTestRenderer.act(() => {
        renderer.root.findByProps({ accessibilityRole: 'tablist' }).props.onLayout({
          nativeEvent: { layout: { width: 320 } },
        });
      });
      const graphCount = graph.mock.calls.length;
      expect(renderer.root.findAllByProps({ blurAmount: 16 })[0].props.autoUpdate).toBe(true);
      await ReactTestRenderer.act(() => {
        renderer.root.findByProps({ accessibilityLabel: '채팅' }).props.onPress();
      });
      expect(renderer.root.findAllByProps({ blurAmount: 16 })[0].props.autoUpdate).toBe(false);
      await ReactTestRenderer.act(() => renderer.update(render('chat')));
      expect(graph).toHaveBeenCalledTimes(graphCount);
      await ReactTestRenderer.act(() => completions[0]({ finished: true }));
      expect(renderer.root.findAllByProps({ blurAmount: 16 })[0].props.autoUpdate).toBe(true);
    } finally {
      await ReactTestRenderer.act(() => renderer?.unmount());
      motion.mockRestore();
      graph.mockRestore();
      platform.mockRestore();
    }
  });

  it('can reverse direction before the screen selection commits', async () => {
    const motion = jest.spyOn(Animated, 'parallel');
    const onSelect = jest.fn();
    let renderer!: ReactTestRenderer.ReactTestRenderer;
    try {
      await ReactTestRenderer.act(() => {
        renderer = ReactTestRenderer.create(
          <BottomNavigation items={items} selectedKey="home" onSelect={onSelect} />,
        );
      });
      await ReactTestRenderer.act(() => {
        renderer.root.findByProps({ accessibilityRole: 'tablist' }).props.onLayout({
          nativeEvent: { layout: { width: 320 } },
        });
      });
      await ReactTestRenderer.act(() => {
        renderer.root.findByProps({ accessibilityLabel: '채팅' }).props.onPress();
        renderer.root.findByProps({ accessibilityLabel: '홈' }).props.onPress();
      });
      expect(onSelect.mock.calls).toEqual([['chat'], ['home']]);
      expect(motion).toHaveBeenCalledTimes(2);
    } finally {
      await ReactTestRenderer.act(() => renderer?.unmount());
      motion.mockRestore();
    }
  });

  it('starts the indicator before switching screens and preserves it after selection commits', async () => {
    const motion = jest.spyOn(Animated, 'parallel');
    const onSelect = jest.fn(() => {
      expect(motion).toHaveBeenCalledTimes(1);
    });
    let renderer!: ReactTestRenderer.ReactTestRenderer;
    const render = (selectedKey: 'home' | 'chat') => (
      <BottomNavigation items={items} selectedKey={selectedKey} onSelect={onSelect} />
    );
    try {
      await ReactTestRenderer.act(() => {
        renderer = ReactTestRenderer.create(render('home'));
      });
      await ReactTestRenderer.act(() => {
        renderer.root.findByProps({ accessibilityRole: 'tablist' }).props.onLayout({
          nativeEvent: { layout: { width: 320 } },
        });
      });
      await ReactTestRenderer.act(() => {
        renderer.root.findByProps({ accessibilityLabel: '채팅' }).props.onPress();
      });
      expect(onSelect).toHaveBeenCalledWith('chat');
      await ReactTestRenderer.act(() => renderer.update(render('chat')));
      expect(motion).toHaveBeenCalledTimes(1);
    } finally {
      await ReactTestRenderer.act(() => renderer?.unmount());
      motion.mockRestore();
    }
  });

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
