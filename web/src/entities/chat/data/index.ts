import { useDemoChat } from '@/mocks/chat/store';

const emptyMessages: ReturnType<typeof useDemoChat.getState>['messages'][string] = [];
export const useConversations = () => useDemoChat((state) => state.conversations);
export const useChatOpen = () => useDemoChat((state) => state.sidebarOpen);
export const useSelectedChatId = () => useDemoChat((state) => state.selectedId);
export const useSetChatOpen = () => useDemoChat((state) => state.setSidebarOpen);
export const useShowChatList = () => useDemoChat((state) => state.showList);
export const useOpenConversation = () => useDemoChat((state) => state.open);
export const useChatMessages = (id: string) =>
  useDemoChat((state) => state.messages[id] ?? emptyMessages);
export const useChatDraft = (id: string) => useDemoChat((state) => state.drafts[id] ?? '');
export const useSetChatDraft = () => useDemoChat((state) => state.setDraft);
export const useSendMessage = () => useDemoChat((state) => state.send);
export const useLeaveConversation = () => useDemoChat((state) => state.leave);
