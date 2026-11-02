const { getDefaultConfig, mergeConfig } = require('@react-native/metro-config');

/**
 * Metro configuration
 * https://reactnative.dev/docs/metro
 *
 * @type {import('@react-native/metro-config').MetroConfig}
 */
const config = {
  resolver: {
    // Gradle recreates these directories while Metro is watching the project.
    blockList: [/[/\\]android[/\\](?:.*[/\\])?(?:build|\.gradle|\.cxx)(?:[/\\]|$)/],
  },
};

module.exports = mergeConfig(getDefaultConfig(__dirname), config);
