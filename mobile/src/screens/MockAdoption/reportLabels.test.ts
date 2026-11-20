import { expenseGroups } from './reportLabels';

test('groups all report expense types once and preserves the total including medical costs', () => {
  const spent = {
    FOOD: 10000,
    FOOD_BOWL: 2000,
    WATER_BOWL: 3000,
    SNACK: 4000,
    TOY: 5000,
    CATNIP: 6000,
    FURNITURE: 7000,
    LITTER_BOX: 8000,
    LITTER: 9000,
    SHAMPOO: 1000,
    GROOMING_KIT: 2000,
    POOP_BAG: 3000,
    LEASH: 4000,
    MEDICINE: 5000,
    EVENT: 6000,
  };
  const groups = expenseGroups(spent);
  expect(groups.map(group => [group.label, group.amount])).toEqual([
    ['식사', 15000],
    ['간식', 4000],
    ['놀이', 11000],
    ['가구', 7000],
    ['위생', 23000],
    ['생활', 15000],
  ]);
  expect(groups.reduce((sum, group) => sum + group.amount, 0)).toBe(
    Object.values(spent).reduce((sum, amount) => sum + amount, 0),
  );
  expect(groups.flatMap(group => group.purchases.map(item => item.key)).sort()).toEqual(
    Object.keys(spent).sort(),
  );
  expect(new Set(groups.map(group => group.color)).size).toBe(6);
});

test('keeps category colors stable when purchases change and handles an empty report', () => {
  const empty = expenseGroups({});
  const changed = expenseGroups({ SNACK: 50000, FOOD: 0, EVENT: 1000 });
  expect(empty.map(group => [group.key, group.color])).toEqual(
    changed.map(group => [group.key, group.color]),
  );
  expect(empty.every(group => group.amount === 0 && group.purchases.length === 0)).toBe(true);
});
