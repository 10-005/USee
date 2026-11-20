import { AppState } from 'react-native';

let visibleRoom: { roomId: string } | null = null;

// Register only while the chat screen is focused, not merely mounted in the stack.
export function trackVisibleChatRoom(roomId: string): () => void {
  const room = { roomId };
  visibleRoom = room;
  return () => {
    if (visibleRoom === room) {
      visibleRoom = null;
    }
  };
}

export function isViewingChatRoom(roomSeq: string): boolean {
  return AppState.currentState === 'active' && visibleRoom?.roomId === roomSeq;
}
