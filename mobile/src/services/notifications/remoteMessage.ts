import type { RemoteMessage } from '@react-native-firebase/messaging';
// TODO: confirm FCM data fields and session identifier. EVENT signals a refresh of
// GET /simulations/{sessionSeq}/events; it is not the SimulationEvent DTO.
// Retain the Firebase entry point without API calls or local resolution.
export async function handleRemoteMessage(_message: RemoteMessage): Promise<void> {}
