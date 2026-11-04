import type { PetCategory } from './data';

const HOUR = 60 * 60 * 1000;
const DAY = 24 * HOUR;

// Adult animals: the selection screen currently has no age information.
export function createToiletSchedule(
  category: PetCategory,
  startedAt: number,
  expiresAt: number,
  random = Math.random,
): number[] {
  const events: number[] = [];
  const count = (min: number, max: number) => min + Math.floor(random() * (max - min + 1));
  if (category === 'dog') {
    for (
      let time = startedAt + (4 + random() * 2) * HOUR;
      time < expiresAt;
      time += (4 + random() * 2) * HOUR
    ) {
      events.push(time);
    }
  }
  for (let day = startedAt; day < expiresAt; day += DAY) {
    const dailyCount = category === 'dog' ? count(1, 3) : count(2, 4) + count(1, 2);
    // Spread random events through the day rather than clustering them together.
    for (let index = 0; index < dailyCount; index++) {
      const time = day + Math.max(1, ((index + random()) / dailyCount) * DAY);
      if (time < expiresAt) {
        events.push(time);
      }
    }
  }
  return events.sort((a, b) => a - b);
}

export function isToiletFull(events: number[], cleanedAt: number, now: number) {
  return events.some(time => time > cleanedAt && time <= now);
}
