import React from 'react';
import ReactTestRenderer, { type ReactTestInstance } from 'react-test-renderer';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import {
  type AdoptionApplicationStatus,
  cancelAdoptionApplication,
  deleteAdoptionDraft,
  getAdoptionApplication,
} from '../../../services/adoption';
import { AdoptionApplicationHistoryScreen } from '../AdoptionApplicationHistoryScreen';

jest.setTimeout(15000);

jest.mock('../../../services/adoption', () => ({
  cancelAdoptionApplication: jest.fn(),
  deleteAdoptionDraft: jest.fn(),
  getAdoptionApplication: jest.fn(),
}));

const mockedGetApplication = getAdoptionApplication as jest.MockedFunction<
  typeof getAdoptionApplication
>;
const mockedCancelApplication = cancelAdoptionApplication as jest.MockedFunction<
  typeof cancelAdoptionApplication
>;
const mockedDeleteDraft = deleteAdoptionDraft as jest.MockedFunction<typeof deleteAdoptionDraft>;

const navigation = {
  goBack: jest.fn(),
  navigate: jest.fn(),
};

const route = {
  key: 'AdoptionApplicationHistory-test',
  name: 'AdoptionApplicationHistory' as const,
  params: {
    animalId: '700',
    applicationSeq: 901,
    applicationStatus: 'SUBMITTED' as const,
    source: 'record' as const,
  },
};

const applicationDetail = {
  animalSummary: {
    age: '2026년생',
    animalSeq: 700,
    breedName: '믹스견',
    colorName: '갈색',
    gender: 'FEMALE' as const,
    isNeutered: true,
    microchipNumber: '410987654321098',
    registrationNumber: '113122600015',
    representativeImageUrl: null,
    shelterAddress: '경상북도 구미시',
    shelterName: '구미보호소',
    shelterSeq: 7,
    speciesName: '개',
    weight: '3kg',
  },
  answers: {},
  applicant: { birthDate: '2000-01-01', name: '김싸피', phone: '010-1234-5678' },
  applicationSeq: 901,
  careAddress: '경상북도 구미시',
  careAddressDetail: null,
  email: 'ssafy@example.com',
  questions: [],
  rejectionReason: '양육 환경을 확인하기 어렵습니다.',
  reviewedDttm: null,
  status: 'SUBMITTED' as const,
  submittedDttm: '2026-11-10T10:00:00+09:00',
  userSeq: 10,
  visitHopeDate: '2026-11-20',
};

function findText(root: ReactTestInstance, value: string) {
  return root.findAll(node => node.props.children === value);
}

function pressButton(root: ReactTestInstance, value: string, last = false) {
  const buttons = root.findAll(
    node =>
      node.props.accessibilityRole === 'button' &&
      node.findAll(child => child.props.children === value).length > 0,
  );
  const button = last ? buttons[buttons.length - 1] : buttons[0];
  if (!button || typeof button.props.onPress !== 'function') {
    throw new Error(`누를 수 있는 '${value}' 문구를 찾지 못했습니다.`);
  }
  button.props.onPress();
}

function pressAction(root: ReactTestInstance, value: string) {
  const actions = root.findAll(
    node => node.props.children === value && typeof node.props.onPress === 'function',
  );
  const action = actions[actions.length - 1];
  if (!action) {
    throw new Error(`실행할 수 있는 '${value}' 버튼을 찾지 못했습니다.`);
  }
  action.props.onPress();
}

async function renderScreen(status: AdoptionApplicationStatus = applicationDetail.status) {
  mockedGetApplication.mockResolvedValue({ ...applicationDetail, status });
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(async () => {
    renderer = ReactTestRenderer.create(
      <SafeAreaProvider
        initialMetrics={{
          frame: { height: 844, width: 390, x: 0, y: 0 },
          insets: { bottom: 34, left: 0, right: 0, top: 47 },
        }}
      >
        <AdoptionApplicationHistoryScreen
          navigation={navigation as never}
          route={route as never}
        />
      </SafeAreaProvider>,
    );
  });
  return renderer;
}

describe('AdoptionApplicationHistoryScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it.each([
    ['DRAFT', '작성중인 신청서입니다'],
    ['SUBMITTED', '신청이 접수되었어요!'],
    ['APPROVED', '입양 심사가 진행 중이에요'],
    ['ADOPTED', '최종 입양이 승인되었어요!'],
    ['REJECTED', '입양이 거절되었어요'],
    ['CANCELED', '입양 신청이 취소되었어요'],
  ] as const)('%s 상태의 진행 문구를 표시한다', async (status, title) => {
    const renderer = await renderScreen(status);
    expect(findText(renderer.root, title).length).toBeGreaterThan(0);
  });

  it('제출된 신청서에서 작성한 신청서 조회 화면으로 이동한다', async () => {
    const renderer = await renderScreen('SUBMITTED');
    ReactTestRenderer.act(() => pressButton(renderer.root, '입양 신청서'));

    expect(navigation.navigate).toHaveBeenCalledWith('AdoptionApplicationReview', {
      animalId: '700',
      applicationSeq: 901,
    });
  });

  it('임시저장 신청서는 작성 화면으로 이동한다', async () => {
    const renderer = await renderScreen('DRAFT');
    ReactTestRenderer.act(() => pressButton(renderer.root, '입양 신청서'));

    expect(navigation.navigate).toHaveBeenCalledWith('AdoptionApplication', {
      animalId: '700',
    });
  });

  it('제출한 신청서를 취소한다', async () => {
    mockedCancelApplication.mockResolvedValue({ applicationSeq: 901, status: 'CANCELED' });
    const renderer = await renderScreen('SUBMITTED');

    ReactTestRenderer.act(() => pressButton(renderer.root, '제출 취소'));
    await ReactTestRenderer.act(async () => pressAction(renderer.root, '제출 취소'));

    expect(mockedCancelApplication).toHaveBeenCalledWith(901);
    expect(findText(renderer.root, '입양 신청이 취소되었어요').length).toBeGreaterThan(0);
  });

  it('임시저장 신청서를 삭제한다', async () => {
    mockedDeleteDraft.mockResolvedValue(undefined);
    const renderer = await renderScreen('DRAFT');

    ReactTestRenderer.act(() => pressButton(renderer.root, '입양신청서 삭제'));
    await ReactTestRenderer.act(async () => pressAction(renderer.root, '삭제하기'));

    expect(mockedDeleteDraft).toHaveBeenCalledWith(901);
    expect(navigation.goBack).toHaveBeenCalled();
  });

  it.each([
    ['SUBMITTED', '제출 취소', '제출 취소', '신청 취소 실패'],
    ['DRAFT', '입양신청서 삭제', '삭제하기', '신청서 삭제 실패'],
  ] as const)('%s 처리 실패를 모달로 안내한다', async (status, openLabel, actionLabel, title) => {
    mockedCancelApplication.mockRejectedValue(new Error('요청을 처리하지 못했습니다.'));
    mockedDeleteDraft.mockRejectedValue(new Error('요청을 처리하지 못했습니다.'));
    const renderer = await renderScreen(status);

    ReactTestRenderer.act(() => pressButton(renderer.root, openLabel));
    await ReactTestRenderer.act(async () => pressAction(renderer.root, actionLabel));

    expect(findText(renderer.root, title).length).toBeGreaterThan(0);
    expect(findText(renderer.root, '요청을 처리하지 못했습니다.').length).toBeGreaterThan(0);
  });
});
