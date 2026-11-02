import { placeDog, projectDog, stepWalk } from './walkMath';

describe('stepWalk', () => {
  it('keeps the dog ahead while the user walks forward', () => {
    let user = { x: 0, z: 0, yaw: 0 };
    let dog = { x: 0, z: 0, yaw: 0 };

    for (let frame = 0; frame < 80; frame += 1) {
      const next = stepWalk({ user, dog, yaw: 0, walking: true, dt: 0.05 });
      user = next.user;
      dog = next.dog;
    }

    expect(user.z).toBeGreaterThan(1);
    expect(dog.z).toBeGreaterThan(user.z);
    expect(dog.x).toBeCloseTo(0.32, 1);
  });

  it('turns the dog toward the direction the camera faces', () => {
    const next = stepWalk({
      user: { x: 0, z: 0, yaw: 0 },
      dog: placeDog(0),
      yaw: Math.PI / 2,
      walking: false,
      dt: 0.05,
    });

    expect(next.dog.x).toBeGreaterThan(placeDog(0).x);
    expect(next.speed).toBeGreaterThan(0);
  });
});

describe('projectDog', () => {
  const view = { width: 400, height: 800, pitch: Math.PI / 2 };

  it('places a dog ahead of the camera near the horizontal center', () => {
    const projected = projectDog({
      user: { x: 0, z: 0, yaw: 0 },
      dog: { x: 0, z: 1.55, yaw: 0 },
      ...view,
    });

    expect(projected.visible).toBe(true);
    expect(projected.x).toBeCloseTo(200, 0);
  });

  it('shifts the dog to the right when it stands on the camera right', () => {
    const centered = projectDog({
      user: { x: 0, z: 0, yaw: 0 },
      dog: { x: 0, z: 1.55, yaw: 0 },
      ...view,
    });
    const onTheRight = projectDog({
      user: { x: 0, z: 0, yaw: 0 },
      dog: { x: 0.8, z: 1.55, yaw: 0 },
      ...view,
    });

    expect(onTheRight.x).toBeGreaterThan(centered.x);
  });
});
