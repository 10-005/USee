/** 채팅 화면에 필요한 데이터. 실제 서버 응답이나 통신 방식은 정하지 않습니다. */
export type Conversation = {
  id: string;
  name: string;
  avatarSrc: string;
  preview: string;
  timeLabel: string;
  unreadCount: number;
};

export type PostKind = 'SHELTER_ANIMAL' | 'MISSING' | 'FOUND';
export type ChatMessage =
  | { id: string; type: 'post'; targetId: string; targetType: PostKind }
  | {
      id: string;
      type: 'message';
      sender: 'other' | 'me';
      text?: string;
      image?: string;
      time: string;
      readByRecipient?: boolean;
    };
