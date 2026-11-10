import type { RemoteMessage } from '@react-native-firebase/messaging';

import { resolveNotificationDestination } from '../pushDestination';
import { parsePushMessage, SERVER_NOTIFICATION_TYPES } from '../pushMessage';
import { handleRemoteMessage, subscribePushMessages } from '../remoteMessage';

const commentMessage = {
  data: {
    commentSeq: '55',
    notificationSeq: '1203',
    notificationType: 'COMMENT',
    targetSeq: '101',
    targetType: 'MISSING',
  },
  notification: {
    body: 'dev105님이 댓글을 남겼어요: 인동시장 근처에서 비슷한 강아지를 봤어요',
    title: '회원님의 게시글에 댓글이 달렸어요',
  },
} as RemoteMessage;

function messageWith(
  data: Record<string, string>,
  notification = commentMessage.notification,
): RemoteMessage {
  return { data, notification } as RemoteMessage;
}

describe('parsePushMessage', () => {
  it('댓글 알림을 서버 메시지로 해석한다', () => {
    expect(parsePushMessage(commentMessage)).toEqual({
      body: commentMessage.notification?.body,
      commentSeq: '55',
      kind: 'server',
      notificationSeq: '1203',
      notificationType: 'COMMENT',
      targetSeq: '101',
      targetType: 'MISSING',
      title: commentMessage.notification?.title,
    });
  });

  it.each(SERVER_NOTIFICATION_TYPES)('%s 타입을 받는다', notificationType => {
    const parsed = parsePushMessage(
      messageWith({
        notificationSeq: '1',
        notificationType,
        targetSeq: '9',
        targetType: 'MISSING',
      }),
    );

    expect(parsed).toMatchObject({ kind: 'server', notificationType });
  });

  it('알 수 없는 타입과 제목이 없는 메시지는 무시한다', () => {
    expect(
      parsePushMessage(messageWith({ notificationSeq: '1', notificationType: 'UNKNOWN' })),
    ).toBeNull();
    expect(
      parsePushMessage({
        data: { notificationSeq: '1', notificationType: 'SYSTEM' },
      } as RemoteMessage),
    ).toBeNull();
  });

  it('모의입양 신호는 대상만으로 구분한다', () => {
    const payloads = [
      ['초코에게 무슨 일이 생겼어요', '물그릇을 엎었어요. 들어와서 살펴봐 주세요.'],
      ['모의 입양이 끝났어요', '초코와의 7일이 끝났어요. 리포트를 확인해 보세요.'],
      ['모의 입양이 중단됐어요', '돌봄이 부족해 초코의 체험이 중단됐어요. 리포트를 확인해 보세요.'],
    ];

    for (const [title, body] of payloads) {
      expect(
        parsePushMessage({
          data: { target: 'SIMULATION' },
          notification: { body, title },
        } as RemoteMessage),
      ).toEqual({ body, kind: 'simulation', title });
    }
  });
});

describe('resolveNotificationDestination', () => {
  it('실종 댓글은 실종 상세로, 보호소 알림은 발견 상세로 보낸다', () => {
    const comment = parsePushMessage(commentMessage)!;
    expect(resolveNotificationDestination(comment)).toEqual({
      highlightCommentId: '55',
      name: 'LostReportDetail',
      reportId: '101',
    });

    const shelter = parsePushMessage(
      messageWith({
        notificationSeq: '8',
        notificationType: 'SHELTER',
        targetSeq: '44',
        targetType: 'FOUND',
      }),
    )!;
    expect(resolveNotificationDestination(shelter)).toEqual({
      name: 'FoundReportDetail',
      reportId: '44',
    });
  });

  it('입양 알림은 신청 이력으로 보내고 매칭과 시스템 알림은 목적지를 정하지 않는다', () => {
    const adoption = parsePushMessage(
      messageWith({
        notificationSeq: '3',
        notificationType: 'ADOPTION_APPROVED',
        targetSeq: 'animal-1',
        targetType: 'ADOPTION',
      }),
    )!;
    expect(resolveNotificationDestination(adoption)).toEqual({
      animalId: 'animal-1',
      name: 'AdoptionApplicationHistory',
    });

    for (const notificationType of ['AI_MATCH', 'SYSTEM'] as const) {
      const parsed = parsePushMessage(
        messageWith({
          notificationSeq: '4',
          notificationType,
          targetSeq: '2',
          targetType: 'MISSING',
        }),
      )!;
      expect(resolveNotificationDestination(parsed)).toBeNull();
    }
  });

  it('모의입양 메시지는 시뮬레이션 갱신 신호로 보낸다', () => {
    const parsed = parsePushMessage(
      messageWith(
        { target: 'SIMULATION' },
        { body: '리포트를 확인해 보세요.', title: '모의 입양이 끝났어요' },
      ),
    )!;

    expect(resolveNotificationDestination(parsed)).toEqual({ name: 'Simulation' });
  });
});

describe('handleRemoteMessage', () => {
  const stops: Array<() => void> = [];

  afterEach(() => {
    stops.splice(0).forEach(stop => stop());
    const drain = subscribePushMessages(() => {});
    drain();
  });

  it('구독자에게 해석된 메시지를 전달하고 잘못된 메시지는 버린다', async () => {
    const received: unknown[] = [];
    stops.push(subscribePushMessages(message => received.push(message)));

    await handleRemoteMessage(commentMessage);
    await handleRemoteMessage({ data: { target: 'SIMULATION' } } as RemoteMessage);

    expect(received).toHaveLength(1);
    expect(received[0]).toMatchObject({ kind: 'server', notificationType: 'COMMENT' });
  });

  it('구독 전에 도착한 메시지를 구독 시 전달한다', async () => {
    await handleRemoteMessage(
      messageWith(
        { target: 'SIMULATION' },
        { body: '살펴봐 주세요.', title: '무슨 일이 생겼어요' },
      ),
    );

    const received: unknown[] = [];
    stops.push(subscribePushMessages(message => received.push(message)));

    expect(received).toEqual([
      { body: '살펴봐 주세요.', kind: 'simulation', title: '무슨 일이 생겼어요' },
    ]);
  });

  it('구독자 오류가 다른 구독자에게 전달되지 않는다', async () => {
    const received: string[] = [];
    stops.push(
      subscribePushMessages(() => {
        throw new Error('listener failed');
      }),
    );
    stops.push(subscribePushMessages(message => received.push(message.kind)));
    const warn = jest.spyOn(console, 'warn').mockImplementation(() => {});

    await expect(handleRemoteMessage(commentMessage)).resolves.toBeUndefined();

    expect(received).toEqual(['server']);
    expect(warn).toHaveBeenCalledWith('[FCM] 알림 처리에 실패했습니다.');
    warn.mockRestore();
  });
});
