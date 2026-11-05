/** 채팅 화면에 필요한 데이터. 실제 서버 응답이나 통신 방식은 정하지 않습니다. */
export type Conversation = {
  id: string;
  name: string;
  avatarSrc: string;
  preview: string;
  timeLabel: string;
  unreadCount: number;
};

export type ChatMessage =
  | { id: string; type: 'post'; animalId: string }
  | {
      id: string;
      type: 'message';
      sender: 'other' | 'me';
      text?: string;
      image?: string;
      time: string;
      readByRecipient?: boolean;
    };
