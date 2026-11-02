import type { TextStyle } from 'react-native';

const fontFamilies = {
  regular: 'Pretendard-Regular',
  medium: 'Pretendard-Medium',
  semiBold: 'Pretendard-SemiBold',
  bold: 'Pretendard-Bold',
} as const;

export const typography = {
  captionRegular10: {
    fontFamily: fontFamilies.regular,
    fontSize: 10,
    lineHeight: 13,
  },
  captionSemiBold12: {
    fontFamily: fontFamilies.semiBold,
    fontSize: 12,
    lineHeight: 15,
  },
  bodyRegular14: {
    fontFamily: fontFamilies.regular,
    fontSize: 14,
    lineHeight: 18,
  },
  bodyRegular16: {
    fontFamily: fontFamilies.regular,
    fontSize: 16,
    lineHeight: 20,
  },
  bodySemiBold16: {
    fontFamily: fontFamilies.semiBold,
    fontSize: 16,
    lineHeight: 20,
  },
  titleSemiBold18: {
    fontFamily: fontFamilies.semiBold,
    fontSize: 18,
    lineHeight: 23,
  },
  titleBold18: {
    fontFamily: fontFamilies.bold,
    fontSize: 18,
    lineHeight: 23,
  },
} as const satisfies Record<string, TextStyle>;

export { fontFamilies };
