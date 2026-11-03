import renderer, { act } from 'react-test-renderer';

import { MyPageScreen } from '../MyPageScreen';

jest.mock('react-native/Libraries/Alert/Alert', () => ({ alert: jest.fn() }));

describe('MyPageScreen', () => {
  it('입양 신청을 누르면 신청 내역으로 이동한다', () => {
    const onOpenAdoptionApplications = jest.fn();
    let screen!: renderer.ReactTestRenderer;

    act(() => {
      screen = renderer.create(
        <MyPageScreen
          onOpenAdoptionApplications={onOpenAdoptionApplications}
          onOpenNotices={jest.fn()}
          onOpenPets={jest.fn()}
          onOpenProfileEdit={jest.fn()}
          onOpenSettings={jest.fn()}
        />,
      );
    });

    const target = screen.root.findByProps({ accessibilityLabel: '입양 신청' });

    act(() => target.props.onPress());
    expect(onOpenAdoptionApplications).toHaveBeenCalledTimes(1);
  });

  it('반려동물 등록을 누르면 내 반려동물 목록으로 이동한다', () => {
    const onOpenPets = jest.fn();
    let screen!: renderer.ReactTestRenderer;

    act(() => {
      screen = renderer.create(
        <MyPageScreen
          onOpenAdoptionApplications={jest.fn()}
          onOpenNotices={jest.fn()}
          onOpenPets={onOpenPets}
          onOpenProfileEdit={jest.fn()}
          onOpenSettings={jest.fn()}
        />,
      );
    });

    act(() => screen.root.findByProps({ accessibilityLabel: '반려동물 등록' }).props.onPress());
    expect(onOpenPets).toHaveBeenCalledTimes(1);
  });

  it('프로필을 누르면 내 정보 수정 화면으로 이동한다', () => {
    const onOpenProfileEdit = jest.fn();
    let screen!: renderer.ReactTestRenderer;

    act(() => {
      screen = renderer.create(
        <MyPageScreen
          onOpenAdoptionApplications={jest.fn()}
          onOpenNotices={jest.fn()}
          onOpenPets={jest.fn()}
          onOpenProfileEdit={onOpenProfileEdit}
          onOpenSettings={jest.fn()}
        />,
      );
    });

    act(() => screen.root.findByProps({ accessibilityLabel: '내 정보 수정' }).props.onPress());
    expect(onOpenProfileEdit).toHaveBeenCalledTimes(1);
  });

  it('환경설정을 누르면 환경설정 화면으로 이동한다', () => {
    const onOpenSettings = jest.fn();
    let screen!: renderer.ReactTestRenderer;

    act(() => {
      screen = renderer.create(
        <MyPageScreen
          onOpenAdoptionApplications={jest.fn()}
          onOpenNotices={jest.fn()}
          onOpenPets={jest.fn()}
          onOpenProfileEdit={jest.fn()}
          onOpenSettings={onOpenSettings}
        />,
      );
    });

    act(() => screen.root.findByProps({ accessibilityLabel: '환경설정' }).props.onPress());
    expect(onOpenSettings).toHaveBeenCalledTimes(1);
  });

  it('공지사항을 누르면 공지사항 목록으로 이동한다', () => {
    const onOpenNotices = jest.fn();
    let screen!: renderer.ReactTestRenderer;

    act(() => {
      screen = renderer.create(
        <MyPageScreen
          onOpenAdoptionApplications={jest.fn()}
          onOpenNotices={onOpenNotices}
          onOpenPets={jest.fn()}
          onOpenProfileEdit={jest.fn()}
          onOpenSettings={jest.fn()}
        />,
      );
    });

    act(() => screen.root.findByProps({ accessibilityLabel: '공지사항' }).props.onPress());
    expect(onOpenNotices).toHaveBeenCalledTimes(1);
  });
});
