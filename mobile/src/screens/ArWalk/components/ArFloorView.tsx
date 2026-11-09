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
  cleanupRequestId?: number;
  canGiveCare?: boolean;
};

type ArFloorNativeProps = ViewProps & {
  active?: boolean;
  showTraversalDebug?: boolean;
  placementRequest?: number;
  actionRequest?: string;
  careRequest?: string;
  bagAvailable?: boolean;
  cleanupResponse?: string;
  onTracking?: (event: NativeSyntheticEvent<FloorTracking>) => void;
  onBagPurchase?: () => void;
};

export const ArFloorView = requireNativeComponent<ArFloorNativeProps>('ArFloorView');
