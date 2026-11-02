import React from 'react';
import ReactTestRenderer from 'react-test-renderer';

import { AnimalCard, ChatListItem } from '..';

const imageSource = { uri: 'https://example.com/image.jpg' };

describe('common list items', () => {
  it('opens an animal card', async () => {
    const onPress = jest.fn();
    let renderer: ReactTestRenderer.ReactTestRenderer;

    await ReactTestRenderer.act(() => {
      renderer = ReactTestRenderer.create(
        <AnimalCard
          date="2026.11.02"
          imageSource={imageSource}
          location="서울시 강남구"
          name="초코"
          onPress={onPress}
          status="보호 중"
        />,
      );
    });

    renderer!.root.findByProps({ accessibilityRole: 'button' }).props.onPress();
    expect(onPress).toHaveBeenCalledTimes(1);
  });

  it('includes the unread count in a chat label', async () => {
    let renderer: ReactTestRenderer.ReactTestRenderer;

    await ReactTestRenderer.act(() => {
      renderer = ReactTestRenderer.create(
        <ChatListItem
          avatarSource={imageSource}
          lastMessage="안녕하세요"
          name="보호자"
          onPress={jest.fn()}
          time="오후 1:20"
          unreadCount={3}
        />,
      );
    });

    expect(
      renderer!.root.findByProps({ accessibilityRole: 'button' }).props.accessibilityLabel,
    ).toContain('읽지 않은 메시지 3개');
  });
});
