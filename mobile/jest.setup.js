/* global jest */

jest.mock(
  '@react-native-async-storage/async-storage',
  () => require('@react-native-async-storage/async-storage/jest').default,
);

jest.mock('@react-native-clipboard/clipboard', () => ({
  __esModule: true,
  default: {
    getString: jest.fn(() => Promise.resolve('')),
    setString: jest.fn(),
  },
}));

jest.mock('@react-native-community/geolocation', () => ({
  __esModule: true,
  default: {
    clearWatch: jest.fn(),
    getCurrentPosition: jest.fn(),
    requestAuthorization: jest.fn(),
    setRNConfiguration: jest.fn(),
    stopObserving: jest.fn(),
    watchPosition: jest.fn(),
  },
}));
