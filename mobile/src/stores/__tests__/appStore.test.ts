import { useAppStore } from '../appStore';

describe('useAppStore', () => {
  beforeEach(() => {
    useAppStore.getState().reset();
  });

  it('marks the app as ready', () => {
    useAppStore.getState().markReady();

    expect(useAppStore.getState().isReady).toBe(true);
  });
});
