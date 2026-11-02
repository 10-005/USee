import React from 'react';
import ReactTestRenderer from 'react-test-renderer';

import { Button, TextInput } from '..';

describe('common form controls', () => {
  it('disables a button while it is loading', async () => {
    let renderer: ReactTestRenderer.ReactTestRenderer;

    await ReactTestRenderer.act(() => {
      renderer = ReactTestRenderer.create(<Button loading>저장</Button>);
    });

    const button = renderer!.root.findByProps({ accessibilityRole: 'button' });
    expect(button.props.disabled).toBe(true);
    expect(button.props.accessibilityState).toEqual({
      busy: true,
      disabled: true,
    });
  });

  it('connects the label and error state to an input', async () => {
    let renderer: ReactTestRenderer.ReactTestRenderer;

    await ReactTestRenderer.act(() => {
      renderer = ReactTestRenderer.create(
        <TextInput errorMessage="필수 입력 항목입니다." label="이름" />,
      );
    });

    const input = renderer!.root.find(node => node.props.accessibilityLabel === '이름');
    expect(input.props.accessibilityLabel).toBe('이름');
    expect(input.props['aria-invalid']).toBe(true);
  });
});
