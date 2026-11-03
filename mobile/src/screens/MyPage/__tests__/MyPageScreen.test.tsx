import renderer, { act } from 'react-test-renderer';

import { MyPageScreen } from '../MyPageScreen';

jest.mock('react-native/Libraries/Alert/Alert', () => ({ alert: jest.fn() }));

describe('MyPageScreen', () => {
  it('입양 신청을 누르면 신청 내역으로 이동한다', () => {
    const onOpenAdoptionApplications = jest.fn();
    let screen!: renderer.ReactTestRenderer;

    act(() => {
      screen = renderer.create(
        <MyPageScreen onOpenAdoptionApplications={onOpenAdoptionApplications} />,
      );
    });

    const target = screen.root.findByProps({ accessibilityLabel: '입양 신청' });

    act(() => target.props.onPress());
    expect(onOpenAdoptionApplications).toHaveBeenCalledTimes(1);
  });
});
