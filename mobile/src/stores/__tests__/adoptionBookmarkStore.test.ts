import { useAdoptionBookmarkStore } from '../adoptionBookmarkStore';

describe('useAdoptionBookmarkStore', () => {
  beforeEach(() => {
    useAdoptionBookmarkStore.getState().reset();
  });

  it('toggles a bookmarked animal', () => {
    useAdoptionBookmarkStore.getState().toggleBookmark('animal-1');
    expect(useAdoptionBookmarkStore.getState().bookmarkedIds).toEqual(['animal-1']);

    useAdoptionBookmarkStore.getState().toggleBookmark('animal-1');
    expect(useAdoptionBookmarkStore.getState().bookmarkedIds).toEqual([]);
  });
});
