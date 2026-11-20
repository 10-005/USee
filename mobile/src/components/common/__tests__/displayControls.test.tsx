import React from 'react';
import ReactTestRenderer from 'react-test-renderer';

import { AppHeader, StepIndicator, Switch } from '..';

describe('common display controls', () => {
  it('exposes a back action in the app header', async () => {
    const onBackPress = jest.fn();
    let renderer: ReactTestRenderer.ReactTestRenderer;

    await ReactTestRenderer.act(() => {
      renderer = ReactTestRenderer.create(<AppHeader onBackPress={onBackPress} title="알림" />);
    });

    renderer!.root.findByProps({ accessibilityLabel: '뒤로 가기' }).props.onPress();
    expect(onBackPress).toHaveBeenCalledTimes(1);
  });

  it('toggles to the next switch value', async () => {
    const onValueChange = jest.fn();
    let renderer: ReactTestRenderer.ReactTestRenderer;

    await ReactTestRenderer.act(() => {
      renderer = ReactTestRenderer.create(
        <Switch label="알림 받기" onValueChange={onValueChange} value={false} />,
      );
    });

    renderer!.root.findByProps({ accessibilityRole: 'switch' }).props.onPress();
    expect(onValueChange).toHaveBeenCalledWith(true);
  });

  it('normalizes progress values', async () => {
    let renderer: ReactTestRenderer.ReactTestRenderer;

    await ReactTestRenderer.act(() => {
      renderer = ReactTestRenderer.create(<StepIndicator currentStep={5} totalSteps={4} />);
    });

    expect(
      renderer!.root.findByProps({ accessibilityRole: 'progressbar' }).props.accessibilityValue,
    ).toEqual({ min: 1, max: 4, now: 4 });
  });
});
