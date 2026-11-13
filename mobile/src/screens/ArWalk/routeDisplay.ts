export type RoutePoint = { latitude: number; longitude: number; timestamp: number };

export const MAX_MAP_POINTS = 2048;
export const MAX_MAP_SEGMENTS = 128;

// Only the display path is simplified. Distance is accumulated from raw accepted motion.
export function appendMapPoint(
  segments: RoutePoint[][],
  point: RoutePoint,
  newSegment = false,
): RoutePoint[][] {
  let result = segments.slice();
  if (newSegment || result.length === 0) {
    result.push([point]);
  } else {
    const tail = result[result.length - 1].slice();
    const a = tail[tail.length - 2];
    const b = tail[tail.length - 1];
    if (a && b && nearlyStraight(a, b, point)) {
      tail[tail.length - 1] = point;
    } else {
      tail.push(point);
    }
    result[result.length - 1] = tail;
  }
  // Preserve the original departure point and never connect across tracking gaps.
  if (result.length > MAX_MAP_SEGMENTS) {
    result = [result[0].slice(0, 1), ...result.slice(-(MAX_MAP_SEGMENTS - 1))];
  }
  while (result.reduce((sum, segment) => sum + segment.length, 0) > MAX_MAP_POINTS) {
    result = result.map(segment =>
      segment.filter((_, index) => index === 0 || index === segment.length - 1 || index % 2 === 0),
    );
  }
  return result;
}

function nearlyStraight(a: RoutePoint, b: RoutePoint, c: RoutePoint): boolean {
  const meters = (Math.PI * 6371000) / 180;
  const longitudeScale = Math.cos((a.latitude * Math.PI) / 180);
  const dx = (c.longitude - a.longitude) * longitudeScale * meters;
  const dy = (c.latitude - a.latitude) * meters;
  const bx = (b.longitude - a.longitude) * longitudeScale * meters;
  const by = (b.latitude - a.latitude) * meters;
  const lengthSquared = dx * dx + dy * dy;
  if (lengthSquared < 0.01) {
    return false;
  }
  const t = (bx * dx + by * dy) / lengthSquared;
  // Preserve U-turns, corners and at least one sample every ten metres.
  return t >= 0 && t <= 1 && lengthSquared <= 100 && Math.hypot(bx - t * dx, by - t * dy) <= 0.25;
}
