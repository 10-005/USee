import renderer, { act } from 'react-test-renderer';
import { Text } from 'react-native';
import { getActiveMockAdoption } from '../../services/storage/mockAdoptionStorage';
import { ensureSessionShopCatalog } from '../../services/simulation/shopItems';
import { useShopSession } from './useShopSession';

jest.mock('../../services/storage/mockAdoptionStorage', () => ({
  getActiveMockAdoption: jest.fn(),
}));

jest.mock('../../services/simulation/shopItems', () => ({
  ensureSessionShopCatalog: jest.fn(),
}));

const session = {
  version: 1 as const,
  petId: 'dog-1',
  weeklyBudget: 100000,
  startedAt: 10,
  expiresAt: Date.now() + 60_000,
  sessionSeq: 3001,
};

function Status({ petId }: { petId: string }) {
  const shopSession = useShopSession(petId);
  return <Text>{shopSession.status}</Text>;
}

beforeEach(() => {
  jest.mocked(getActiveMockAdoption).mockReset();
  jest.mocked(ensureSessionShopCatalog).mockReset();
  jest.mocked(ensureSessionShopCatalog).mockResolvedValue(undefined);
});

it('loads the server shop catalog before opening a server session', async () => {
  jest.mocked(getActiveMockAdoption).mockResolvedValue(session);
  let screen!: renderer.ReactTestRenderer;
  await act(async () => {
    screen = renderer.create(<Status petId="dog-1" />);
  });
  expect(ensureSessionShopCatalog).toHaveBeenCalledWith('dog-1:10', 'DOG');
  expect(screen.root.findByType(Text).props.children).toBe('ready');
});

it('keeps the saved catalog when the adoption has no server session', async () => {
  jest.mocked(getActiveMockAdoption).mockResolvedValue({ ...session, sessionSeq: undefined });
  await act(async () => {
    renderer.create(<Status petId="dog-1" />);
  });
  expect(ensureSessionShopCatalog).not.toHaveBeenCalled();
});
