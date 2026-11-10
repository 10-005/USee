jest.mock('../../services/bookmarks', () => ({
  addBookmark: jest.fn(),
  bookmarkKey: (type: string, seq: number | string) => `${type}:${seq}`,
  getAllBookmarks: jest.fn(),
  removeBookmark: jest.fn(),
}));

import { addBookmark, getAllBookmarks, removeBookmark } from '../../services/bookmarks';
import { useAdoptionBookmarkStore } from '../adoptionBookmarkStore';

describe('useAdoptionBookmarkStore', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    useAdoptionBookmarkStore.getState().reset();
  });

  it('서버 북마크 목록을 대상 키로 저장한다', async () => {
    (getAllBookmarks as jest.Mock).mockResolvedValue([
      { targetSeq: 11, targetType: 'SHELTER_ANIMAL' },
    ]);

    await useAdoptionBookmarkStore.getState().loadBookmarks('SHELTER_ANIMAL');

    expect(useAdoptionBookmarkStore.getState().bookmarkKeys).toEqual(['SHELTER_ANIMAL:11']);
  });

  it('북마크를 낙관적으로 등록하고 해제한다', async () => {
    (addBookmark as jest.Mock).mockResolvedValue({});
    (removeBookmark as jest.Mock).mockResolvedValue(undefined);

    await useAdoptionBookmarkStore.getState().toggleBookmark('FOUND', 21);
    expect(useAdoptionBookmarkStore.getState().bookmarkKeys).toEqual(['FOUND:21']);
    expect(addBookmark).toHaveBeenCalledWith('FOUND', 21);

    await useAdoptionBookmarkStore.getState().toggleBookmark('FOUND', 21);
    expect(useAdoptionBookmarkStore.getState().bookmarkKeys).toEqual([]);
    expect(removeBookmark).toHaveBeenCalledWith('FOUND', 21);
  });

  it('같은 북마크 요청이 진행 중이면 중복 요청하지 않는다', async () => {
    let resolveRequest!: () => void;
    (addBookmark as jest.Mock).mockReturnValue(
      new Promise<void>(resolve => {
        resolveRequest = resolve;
      }),
    );

    const first = useAdoptionBookmarkStore.getState().toggleBookmark('MISSING', 31);
    const second = useAdoptionBookmarkStore.getState().toggleBookmark('MISSING', 31);

    expect(useAdoptionBookmarkStore.getState().pendingKeys).toEqual(['MISSING:31']);
    expect(addBookmark).toHaveBeenCalledTimes(1);

    resolveRequest();
    await Promise.all([first, second]);
    expect(useAdoptionBookmarkStore.getState().pendingKeys).toEqual([]);
  });
});
