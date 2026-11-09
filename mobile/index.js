/**
 * @format
 */

import { AppRegistry } from 'react-native';
import App from './App';
import { name as appName } from './app.json';
import { registerBackgroundMessaging } from './src/services/notifications/firebaseMessaging';

registerBackgroundMessaging();

AppRegistry.registerComponent(appName, () => App);
