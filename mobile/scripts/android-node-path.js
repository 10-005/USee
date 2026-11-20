const fs = require('fs');
const path = require('path');

const nodeFunction = `fun nodeExecutable(): String {
    val candidates = listOf(
        System.getenv("NODE_BINARY"),
        "/opt/homebrew/bin/node",
        "/usr/local/bin/node",
    )
    return candidates.firstOrNull { candidate ->
        candidate != null && File(candidate).canExecute()
    } ?: "node"
}

`;

const bundledLookup = `    val bundledReactNative = file("\${rootDir}/../node_modules/react-native")
    if (bundledReactNative.resolve("package.json").exists()) {
        return bundledReactNative
    }

`;

const targets = [
  'react-native-reanimated/android/build.gradle.kts',
  'react-native-worklets/android/build.gradle.kts',
];

for (const relativePath of targets) {
  const filePath = path.join(__dirname, '../node_modules', relativePath);
  if (!fs.existsSync(filePath)) {
    continue;
  }

  let source = fs.readFileSync(filePath, 'utf8');
  if (!source.includes('fun nodeExecutable(): String')) {
    source = source.replace(
      'if (shouldApplyKotlinAndroidPlugin()) {\n    apply(plugin = "org.jetbrains.kotlin.android")\n}\n',
      `if (shouldApplyKotlinAndroidPlugin()) {\n    apply(plugin = "org.jetbrains.kotlin.android")\n}\n\n${nodeFunction}`,
    );
  }
  if (!source.includes('bundledReactNative')) {
    source = source.replace(
      'fun resolveReactNativeDirectory(): File {\n',
      `fun resolveReactNativeDirectory(): File {\n${bundledLookup}`,
    );
  }
  source = source.replaceAll('commandLine("node",', 'commandLine(nodeExecutable(),');
  fs.writeFileSync(filePath, source);
}

const naverMapShadowNode = path.join(
  __dirname,
  '../node_modules/@mj-studio/react-native-naver-map/android/src/main/java/com/mjstudio/reactnativenavermap/util/view/SizeReportingShadowNode.kt',
);

if (fs.existsSync(naverMapShadowNode)) {
  let source = fs.readFileSync(naverMapShadowNode, 'utf8');
  source = source
    .replace('data["width"] = layoutWidth', 'data["width"] = getLayoutWidth()')
    .replace('data["height"] = layoutHeight', 'data["height"] = getLayoutHeight()')
    .replace(
      'uiViewOperationQueue.enqueueUpdateExtraData(reactTag, data)',
      'uiViewOperationQueue.enqueueUpdateExtraData(getReactTag(), data)',
    );
  fs.writeFileSync(naverMapShadowNode, source);
}
