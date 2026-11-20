import { create } from 'zustand';

import { ApiClientError, getApiErrorDisplayMessage } from '../services/api';
import {
  addBookmark,
  bookmarkKey,
  getAllBookmarks,
  removeBookmark,
  type BookmarkItem,
  type BookmarkTargetType,
} from '../services/bookmarks';

type AdoptionBookmarkState = {
  bookmarkKeys: string[];
  error: string | null;
  isLoading: boolean;
  items: BookmarkItem[];
  loadBookmarks: (targetType?: BookmarkTargetType) => Promise<void>;
  pendingKeys: string[];
  reset: () => void;
  toggleBookmark: (targetType: BookmarkTargetType, targetSeq: number | string) => Promise<void>;
};

export const useAdoptionBookmarkStore = create<AdoptionBookmarkState>(set => ({
  bookmarkKeys: [],
  error: null,
  isLoading: false,
  items: [],
  loadBookmarks: async targetType => {
    set({ error: null, isLoading: true });
    try {
      const items = await getAllBookmarks(targetType);
      set(state => {
        const retainedItems = targetType
          ? state.items.filter(item => item.targetType !== targetType)
          : [];
        const nextItems = [...retainedItems, ...items];
        return {
          bookmarkKeys: nextItems.map(item => bookmarkKey(item.targetType, item.targetSeq)),
          items: nextItems,
        };
      });
    } catch (error) {
      set({ error: getApiErrorDisplayMessage(error, '저장한 게시글을 불러오지 못했어요.') });
      throw error;
    } finally {
      set({ isLoading: false });
    }
  },
  pendingKeys: [],
  reset: () => set({ bookmarkKeys: [], error: null, items: [], pendingKeys: [] }),
  toggleBookmark: async (targetType, targetSeqValue) => {
    const targetSeq = Number(targetSeqValue);
    const key = bookmarkKey(targetType, targetSeq);
    const currentState = useAdoptionBookmarkStore.getState();
    if (currentState.pendingKeys.includes(key)) {
      return;
    }
    const wasBookmarked = currentState.bookmarkKeys.includes(key);
    set(state => ({
      bookmarkKeys: wasBookmarked
        ? state.bookmarkKeys.filter(item => item !== key)
        : [...state.bookmarkKeys, key],
      error: null,
      items: wasBookmarked
        ? state.items.filter(
            item => !(item.targetType === targetType && item.targetSeq === targetSeq),
          )
        : state.items,
      pendingKeys: [...state.pendingKeys, key],
    }));
    try {
      if (wasBookmarked) {
        await removeBookmark(targetType, targetSeq);
      } else {
        await addBookmark(targetType, targetSeq);
      }
    } catch (error) {
      const alreadyMatchesServer =
        error instanceof ApiClientError &&
        ((!wasBookmarked && error.code === 'USER_BOOKMARK_ALREADY_EXISTS') ||
          (wasBookmarked && error.code === 'USER_BOOKMARK_NOT_FOUND'));
      if (alreadyMatchesServer) {
        return;
      }
      set(state => ({
        bookmarkKeys: wasBookmarked
          ? [...state.bookmarkKeys, key]
          : state.bookmarkKeys.filter(item => item !== key),
        error: getApiErrorDisplayMessage(error, '북마크를 변경하지 못했어요.'),
      }));
      throw error;
    } finally {
      set(state => ({ pendingKeys: state.pendingKeys.filter(item => item !== key) }));
    }
  },
}));
