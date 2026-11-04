import { requireNativeComponent, type NativeSyntheticEvent, type ViewProps } from 'react-native';

export type FloorTrackingStatus =
  | 'searching'
  | 'analyzing'
  | 'recognitionError'
  | 'ready'
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
  moving?: boolean;
  onScreen?: boolean;
  userMoving?: boolean;
};

type ArFloorNativeProps = ViewProps & {
  petName?: string;
  active?: boolean;
  showTraversalDebug?: boolean;
  placementRequest?: number;
  actionRequest?: string;
  onTracking?: (event: NativeSyntheticEvent<FloorTracking>) => void;
};

export const ArFloorView = requireNativeComponent<ArFloorNativeProps>('ArFloorView');
