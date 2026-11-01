import { requireNativeComponent, type NativeSyntheticEvent, type ViewProps } from 'react-native';

export type FloorTrackingStatus =
  | 'searching'
  | 'anchored'
  | 'lost'
  | 'unsupported'
  | 'needsInstall'
  | 'error';

export type FloorTracking = {
  status: FloorTrackingStatus;
  visible: boolean;
  x: number;
  y: number;
  scale: number;
  facing: number;
  clip?: string;
};

type ArFloorNativeProps = ViewProps & {
  active?: boolean;
  onTracking?: (event: NativeSyntheticEvent<FloorTracking>) => void;
};

export const ArFloorView = requireNativeComponent<ArFloorNativeProps>('ArFloorView');
