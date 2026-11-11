import { NativeModules, Platform } from 'react-native';

import { showPushBanner } from '../pushBanner';

it('안드로이드에서 제목과 본문으로 배너를 띄운다', async () => {
  const show = jest.fn().mockResolvedValue(null);
  NativeModules.PushBanner = { show };
  jest.replaceProperty(Platform, 'OS', 'android');

  await showPushBanner('댓글이 달렸어요', '인동시장 근처에서 봤어요');

  expect(show).toHaveBeenCalledWith('댓글이 달렸어요', '인동시장 근처에서 봤어요');
});

it('배너 모듈이 없으면 조용히 지나간다', async () => {
  NativeModules.PushBanner = undefined;
  jest.replaceProperty(Platform, 'OS', 'android');

  await expect(showPushBanner('제목', '본문')).resolves.toBeUndefined();
});
