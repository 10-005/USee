/* global jest */

jest.mock(
  '@react-native-async-storage/async-storage',
  () => require('@react-native-async-storage/async-storage/jest').default,
);

jest.mock('react-native-reanimated', () => {
  const React = require('react');
  const { View } = require('react-native');

  return {
    __esModule: true,
    default: {
      View: props => React.createElement(View, props),
    },
    SensorType: {
      ACCELEROMETER: 1,
      ROTATION: 5,
    },
    runOnJS: fn => fn,
    useAnimatedReaction: () => undefined,
    useAnimatedSensor: () => ({
      sensor: {
        value: {
          qw: 0,
          qx: 0,
          qy: 0,
          qz: 0,
          yaw: 0,
          pitch: 0,
          roll: 0,
          x: 0,
          y: 0,
          z: 0,
        },
      },
    }),
    useAnimatedStyle: updater => updater(),
    useFrameCallback: () => undefined,
    useSharedValue: value => ({ value }),
  };
});

jest.mock('react-native-vision-camera', () => {
  const React = require('react');
  const { View } = require('react-native');

  return {
    Camera: props => React.createElement(View, props),
    useCameraDevice: () => ({ id: 'back' }),
    useCameraPermission: () => ({
      hasPermission: true,
      requestPermission: async () => true,
      canRequestPermission: false,
      status: 'authorized',
    }),
  };
});

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
