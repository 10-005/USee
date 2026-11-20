import { createToiletSchedule, isToiletFull } from './toiletSchedule';

const HOUR = 60 * 60 * 1000;
const DAY = 24 * HOUR;

test('dogs urinate every 4–6 hours and defecate 1–3 times per day', () => {
  // A midpoint random source gives 5-hour intervals and 2 stools per day.
  const events = createToiletSchedule('dog', 0, 2 * DAY, () => 0.5);
  const urine = Array.from({ length: 9 }, (_, index) => (index + 1) * 5 * HOUR);
  expect(events).toEqual(
    [...urine, 6 * HOUR, 18 * HOUR, 30 * HOUR, 42 * HOUR].sort((a, b) => a - b),
  );
});

test.each([0, 0.999])('cats have 3–6 combined daily events with random=%s', random => {
  const events = createToiletSchedule('cat', 0, 3 * DAY, () => random);
  for (let day = 0; day < 3; day++) {
    const count = events.filter(time => time >= day * DAY && time < (day + 1) * DAY).length;
    expect(count).toBe(random === 0 ? 3 : 6);
  }
});

test('cleaning removes past events, and a later event fills the toilet again', () => {
  const events = [100, 200, 300];
  expect(isToiletFull(events, 0, 99)).toBe(false);
  expect(isToiletFull(events, 0, 200)).toBe(true);
  expect(isToiletFull(events, 200, 250)).toBe(false);
  expect(isToiletFull(events, 200, 300)).toBe(true);
});
