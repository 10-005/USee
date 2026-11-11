import { NativeModules, Platform } from 'react-native';

import { showPushBanner } from '../pushBanner';

it('안드로이드에서 제목과 본문으로 배너를 띄운다', async () => {
  const show = jest.fn().mockResolvedValue(null);
  NativeModules.PushBanner = { show };
  jest.replaceProperty(Platform, 'OS', 'android');

  await showPushBanner('댓글이 달렸어요', '인동시장 근처에서 봤어요');

  expect(show).toHaveBeenCalledWith(
    '댓글이 달렸어요',
    '인동시장 근처에서 봤어요',
    JSON.stringify({
      notification: { title: '댓글이 달렸어요', body: '인동시장 근처에서 봤어요' },
    }),
  );
});

it('알림마다 클릭에 필요한 채팅방 정보를 네이티브에 전달한다', async () => {
  const show = jest.fn().mockResolvedValue(null);
  NativeModules.PushBanner = { show };
  jest.replaceProperty(Platform, 'OS', 'android');
  const message = { data: { notificationType: 'CHAT', roomSeq: '501', messageSeq: '9001' } };
  await showPushBanner('보호센터', '메시지', message);
  expect(JSON.parse(show.mock.calls[0][2])).toEqual({
    data: message.data,
    notification: { title: '보호센터', body: '메시지' },
  });
});

it('배너 모듈이 없으면 조용히 지나간다', async () => {
  NativeModules.PushBanner = undefined;
  jest.replaceProperty(Platform, 'OS', 'android');

  await expect(showPushBanner('제목', '본문')).resolves.toBeUndefined();
});
