import { appendMapPoint, MAX_MAP_POINTS, MAX_MAP_SEGMENTS, type RoutePoint } from './routeDisplay';

const point = (x: number, y = 0): RoutePoint => ({
  latitude: 37 + y / 111195,
  longitude: 127 + x / 88700,
  timestamp: x + y,
});

test('straight display paths simplify while preserving turns, endpoints and input arrays', () => {
  const start = point(0);
  const first = appendMapPoint([], start);
  const second = appendMapPoint(first, point(1));
  const straight = appendMapPoint(second, point(2));
  expect(first).toEqual([[start]]);
  expect(second[0]).toHaveLength(2);
  expect(straight).toEqual([[start, point(2)]]);
  const turn = appendMapPoint(straight, point(2, 2));
  expect(turn[0]).toHaveLength(3);
  const back = appendMapPoint(straight, start);
  expect(back[0]).toHaveLength(3);
});

test('long and interrupted display histories stay bounded without joining gaps', () => {
  let segments: RoutePoint[][] = [];
  const start = point(0);
  for (let i = 0; i < 10000; i++) {
    segments = appendMapPoint(segments, point(i, i % 2));
  }
  expect(segments.flat().length).toBeLessThanOrEqual(MAX_MAP_POINTS);
  expect(segments[0][0]).toEqual(start);
  expect(segments.at(-1)?.at(-1)).toEqual(point(9999, 1));
  for (let i = 0; i < 200; i++) {
    segments = appendMapPoint(segments, point(i + 20000), true);
  }
  expect(segments.length).toBeLessThanOrEqual(MAX_MAP_SEGMENTS);
  expect(segments[0][0]).toEqual(start);
  expect(segments.at(-1)).toEqual([point(20199)]);
});
