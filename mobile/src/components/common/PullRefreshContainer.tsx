import { Platform, requireNativeComponent, View, type ViewProps } from 'react-native';

export default Platform.OS === 'android'
  ? requireNativeComponent<ViewProps>('PullRefreshContainer')
  : View;
