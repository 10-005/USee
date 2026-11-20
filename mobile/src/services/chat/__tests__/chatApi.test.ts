jest.mock('../../api/client', () => ({
  chatClient: { get: jest.fn(), patch: jest.fn(), post: jest.fn() },
}));

import { chatClient } from '../../api/client';
import {
  createChatRoom,
  getChatMessages,
  getChatRooms,
  leaveChatRoom,
  markChatRoomRead,
} from '../chatApi';

describe('chatApi', () => {
  beforeEach(() => jest.clearAllMocks());

  it('대화방 목록과 메시지를 커서 방식으로 조회한다', async () => {
    (chatClient.get as jest.Mock).mockResolvedValue({ data: { data: { content: [] } } });
    await getChatRooms(true, 40);
    await getChatMessages(7, 30);
    expect(chatClient.get).toHaveBeenNthCalledWith(1, '/chats/rooms', {
      params: { beforeLastMessageSeq: 40, size: 20, unreadOnly: true },
    });
    expect(chatClient.get).toHaveBeenNthCalledWith(2, '/chats/rooms/7/messages', {
      params: { beforeMessageSeq: 30, size: 30 },
    });
  });

  it('회원과 보호소 대상에 맞는 방 생성 요청을 보낸다', async () => {
    (chatClient.post as jest.Mock).mockResolvedValue({ data: { data: { chatRoomSeq: 1 } } });
    await createChatRoom('USER', 11);
    await createChatRoom('SHELTER', 22);
    expect(chatClient.post).toHaveBeenNthCalledWith(1, '/chats/rooms', {
      targetType: 'USER',
      targetUserSeq: 11,
    });
    expect(chatClient.post).toHaveBeenNthCalledWith(2, '/chats/rooms', {
      targetShelterSeq: 22,
      targetType: 'SHELTER',
    });
  });

  it('읽음 처리와 나가기를 요청한다', async () => {
    (chatClient.patch as jest.Mock).mockResolvedValue({ data: { data: {} } });
    (chatClient.post as jest.Mock).mockResolvedValue({ data: { data: {} } });
    await markChatRoomRead(3, 99);
    await leaveChatRoom(3);
    expect(chatClient.patch).toHaveBeenCalledWith('/chats/rooms/3/read', {
      lastReadMessageSeq: 99,
    });
    expect(chatClient.post).toHaveBeenCalledWith('/chats/rooms/3/leave');
  });
});
