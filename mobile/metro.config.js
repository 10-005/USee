const fs = require('fs');
const path = require('path');
const { getDefaultConfig, mergeConfig } = require('@react-native/metro-config');

/**
 * Metro configuration
 * https://reactnative.dev/docs/metro
 *
 * @type {import('@react-native/metro-config').MetroConfig}
 */
const defaultConfig = getDefaultConfig(__dirname);
const { assetExts, sourceExts } = defaultConfig.resolver;

const config = {
  transformer: {
    babelTransformerPath: require.resolve('react-native-svg-transformer/react-native'),
  },
  resolver: {
    blockList: [/[/\\]android[/\\](?:.*[/\\])?(?:build|\.gradle|\.cxx)(?:[/\\]|$)/],
    assetExts: assetExts.filter(ext => ext !== 'svg'),
    sourceExts: [...sourceExts, 'svg'],
    resolveRequest: (context, moduleName, platform) => {
      if (moduleName === './accessToken.local') {
        const localFile = path.join(path.dirname(context.originModulePath), 'accessToken.local.ts');
        if (!fs.existsSync(localFile)) {
          return {
            filePath: path.join(
              path.dirname(context.originModulePath),
              'accessToken.local.stub.ts',
            ),
            type: 'sourceFile',
          };
        }
      }

      return context.resolveRequest(context, moduleName, platform);
    },
  },
};

module.exports = mergeConfig(defaultConfig, config);
