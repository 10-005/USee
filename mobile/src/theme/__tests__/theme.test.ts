import { colors, fontFamilies, radius, typography } from '..';

describe('theme', () => {
  it('exposes the primary brand color', () => {
    expect(colors.green400).toBe('#009D58');
  });

  it('uses the bundled Pretendard font family', () => {
    expect(fontFamilies.semiBold).toBe('Pretendard-SemiBold');
    expect(typography.bodySemiBold16.fontSize).toBe(16);
  });

  it('provides the common component radius', () => {
    expect(radius.md).toBe(8);
  });
});
