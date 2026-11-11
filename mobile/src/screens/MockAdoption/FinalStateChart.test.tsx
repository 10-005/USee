import ReactTestRenderer from 'react-test-renderer';
import { Circle, LinearGradient, RadialGradient, Stop, Text as SvgText } from 'react-native-svg';
import { FinalStateChart } from './reportCharts';

test('matches each vertex and gradient to its percentage text across status thresholds', async () => {
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  try {
    await ReactTestRenderer.act(() => {
      renderer = ReactTestRenderer.create(
        <FinalStateChart
          stats={{
            satiety: 39,
            hydration: 40,
            cleanliness: 69,
            bond: 70,
            calmness: 100,
            health: 0,
            energy: 80,
          }}
        />,
      );
    });
    const expected = ['#A63828', '#89500E', '#89500E', '#036666', '#036666', '#A63828', '#036666'];
    const vertices = renderer.root.findAllByType(Circle).filter(node => node.props.r === 3.2);
    const percentages = renderer.root
      .findAllByType(SvgText)
      .filter(node => String(node.props.children).endsWith('%'));
    expect(vertices.map(node => node.props.fill)).toEqual(expected);
    expect(percentages.map(node => node.props.fill)).toEqual(expected);
    const fills = renderer.root
      .findAllByType(RadialGradient)
      .filter(node => node.props.gradientUnits === 'userSpaceOnUse');
    fills.forEach((gradient, index) => {
      const stops = gradient.findAllByType(Stop);
      expect(stops.every(stop => stop.props.stopColor === expected[index])).toBe(true);
      expect(stops[0].props.stopOpacity).toBeGreaterThan(stops[stops.length - 1].props.stopOpacity);
    });
    renderer.root.findAllByType(LinearGradient).forEach((gradient, index) => {
      expect(gradient.findAllByType(Stop).map(stop => stop.props.stopColor)).toEqual([
        expected[index],
        expected[(index + 1) % expected.length],
      ]);
    });
  } finally {
    await ReactTestRenderer.act(() => renderer?.unmount());
  }
});
