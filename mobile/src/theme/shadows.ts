import type { ViewStyle } from 'react-native';

export const shadows = {
  level1: {
    elevation: 2,
    shadowColor: '#272727',
    shadowOffset: { height: 2, width: 0 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
  },
  level2: {
    elevation: 4,
    shadowColor: '#000000',
    shadowOffset: { height: 4, width: 0 },
    shadowOpacity: 0.15,
    shadowRadius: 6,
  },
} as const satisfies Record<string, ViewStyle>;
