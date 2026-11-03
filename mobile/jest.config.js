module.exports = {
  moduleNameMapper: {
    '\\.svg$': '<rootDir>/__mocks__/svgMock.js',
  },
  preset: '@react-native/jest-preset',
  setupFiles: ['<rootDir>/jest.setup.js'],
  transformIgnorePatterns: [
    'node_modules/(?!((@)?react-native|@react-native-community|@react-native-async-storage|@react-navigation|react-native-image-picker)/)',
  ],
};
