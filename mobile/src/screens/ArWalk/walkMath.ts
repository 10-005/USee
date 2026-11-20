export const LEAD_DISTANCE = 1.55;
export const SIDE_OFFSET = 0.32;

const USER_WALK_SPEED = 1.15;
const DOG_WALK_SPEED = 1.7;
const DOG_TURN_SPEED = 1.05;
const MAX_FRAME_SECONDS = 0.05;

export type Pose = {
  x: number;
  z: number;
  yaw: number;
};

export type DogProjection = {
  x: number;
  y: number;
  scale: number;
  visible: boolean;
  facing: number;
};

export function wrapAngle(angle: number) {
  'worklet';
  const fullTurn = Math.PI * 2;
  const wrapped = (angle + Math.PI) % fullTurn;
  return (wrapped < 0 ? wrapped + fullTurn : wrapped) - Math.PI;
}

export function forward(yaw: number) {
  'worklet';
  return {
    x: Math.sin(yaw),
    z: Math.cos(yaw),
  };
}

export function right(yaw: number) {
  'worklet';
  return {
    x: Math.cos(yaw),
    z: -Math.sin(yaw),
  };
}

export function placeDog(yaw: number): Pose {
  'worklet';
  const ahead = forward(yaw);
  const beside = right(yaw);
  return {
    x: ahead.x * LEAD_DISTANCE + beside.x * SIDE_OFFSET,
    z: ahead.z * LEAD_DISTANCE + beside.z * SIDE_OFFSET,
    yaw,
  };
}

export function accelerationEnergy(x: number, y: number, z: number) {
  'worklet';
  const magnitude = Math.sqrt(x * x + y * y + z * z);
  return Math.abs(magnitude - 9.81);
}

export function stepWalk(input: {
  user: Pose;
  dog: Pose;
  yaw: number;
  walking: boolean;
  dt: number;
}) {
  'worklet';
  const dt = Math.min(Math.max(input.dt, 0), MAX_FRAME_SECONDS);
  const ahead = forward(input.yaw);
  const beside = right(input.yaw);
  const userX = input.user.x + (input.walking ? ahead.x * USER_WALK_SPEED * dt : 0);
  const userZ = input.user.z + (input.walking ? ahead.z * USER_WALK_SPEED * dt : 0);
  const targetX = userX + ahead.x * LEAD_DISTANCE + beside.x * SIDE_OFFSET;
  const targetZ = userZ + ahead.z * LEAD_DISTANCE + beside.z * SIDE_OFFSET;
  const offsetX = targetX - input.dog.x;
  const offsetZ = targetZ - input.dog.z;
  const distance = Math.hypot(offsetX, offsetZ);
  const speedLimit = input.walking ? DOG_WALK_SPEED : DOG_TURN_SPEED;
  const step = Math.min(distance, speedLimit * dt);
  const directionX = distance > 0.001 ? offsetX / distance : ahead.x;
  const directionZ = distance > 0.001 ? offsetZ / distance : ahead.z;

  return {
    user: { x: userX, z: userZ, yaw: input.yaw },
    dog: {
      x: input.dog.x + directionX * step,
      z: input.dog.z + directionZ * step,
      yaw: distance > 0.05 ? Math.atan2(directionX, directionZ) : input.dog.yaw,
    },
    speed: dt > 0 ? step / dt : 0,
  };
}

export function projectDog(input: {
  user: Pose;
  dog: Pose;
  pitch: number;
  width: number;
  height: number;
}): DogProjection {
  'worklet';
  const offsetX = input.dog.x - input.user.x;
  const offsetZ = input.dog.z - input.user.z;
  const ahead = forward(input.user.yaw);
  const beside = right(input.user.yaw);
  const cameraRight = offsetX * beside.x + offsetZ * beside.z;
  const cameraForward = offsetX * ahead.x + offsetZ * ahead.z;
  const depth = Math.max(cameraForward, 0.3);
  const focal = input.width * 0.9;
  const rawX = input.width / 2 + (cameraRight / depth) * focal;
  const lookDown = Math.PI / 2 - Math.abs(input.pitch);
  const rawY = input.height * 0.72 - lookDown * input.height * 0.38;
  const marginX = input.width * 0.14;
  const marginTop = input.height * 0.22;
  const marginBottom = input.height * 0.12;

  return {
    x: Math.min(input.width - marginX, Math.max(marginX, rawX)),
    y: Math.min(input.height - marginBottom, Math.max(marginTop, rawY)),
    scale: Math.min(1.65, Math.max(0.55, 1.25 / depth)),
    visible: cameraForward > 0.3,
    facing: wrapAngle(input.dog.yaw - input.user.yaw) > 0 ? -1 : 1,
  };
}
