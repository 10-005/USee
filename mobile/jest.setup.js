/* global jest */

jest.mock('@react-native-community/blur', () => ({
  BlurView: require('react-native').View,
}));

jest.mock('@react-native-firebase/messaging', () => ({
  getMessaging: jest.fn(() => ({})),
  getToken: jest.fn().mockResolvedValue('test-fcm-token'),
  getInitialNotification: jest.fn().mockResolvedValue(null),
  onMessage: jest.fn(() => jest.fn()),
  onNotificationOpenedApp: jest.fn(() => jest.fn()),
  onTokenRefresh: jest.fn(() => jest.fn()),
  setBackgroundMessageHandler: jest.fn(),
}));

jest.mock('@op-engineering/op-sqlite', () => require('./__mocks__/opSqliteMock'));

jest.mock('@react-native-seoul/kakao-login', () => ({
  login: jest.fn().mockResolvedValue({ accessToken: 'kakao-access-token' }),
}));

jest.mock('react-native-keychain', () => ({
  ACCESSIBLE: { WHEN_UNLOCKED_THIS_DEVICE_ONLY: 'WhenUnlockedThisDeviceOnly' },
  getGenericPassword: jest.fn().mockResolvedValue(false),
  resetGenericPassword: jest.fn().mockResolvedValue(true),
  setGenericPassword: jest.fn().mockResolvedValue(true),
}));

jest.mock('@dr.pogodin/react-native-fs', () => ({
  DocumentDirectoryPath: '/documents',
  copyFile: jest.fn(),
  mkdir: jest.fn(),
  unlink: jest.fn(),
}));

jest.mock('react-native-image-picker', () => ({
  launchImageLibrary: jest.fn().mockResolvedValue({ didCancel: true }),
}));

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
      createAnimatedComponent: component => component,
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

jest.mock('@mj-studio/react-native-naver-map', () => ({
  NaverMapView: 'NaverMapView',
  NaverMapMarkerOverlay: 'NaverMapMarkerOverlay',
}));
