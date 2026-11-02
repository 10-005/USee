export const colors = {
  white: '#FFFFFF',
  black: '#141414',
  gray10: '#FAFAF6',
  gray200: '#D7D6CF',
  gray300: '#B9B8B0',
  gray400: '#95948D',
  gray500: '#72716C',
  gray600: '#53524D',
  gray700: '#363532',
  green100: '#22C55E',
  green200: '#17B85C',
  green400: '#009D58',
  blue: '#5B9DFA',
  danger: '#E5484D',
  transparent: 'transparent',
} as const;

export type ColorToken = keyof typeof colors;
