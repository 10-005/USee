# SegFormer B0 / ADE20K

Bundled for on-device indoor/outdoor AR walking evaluation. Camera images do not leave the device.

- Original checkpoint: https://huggingface.co/nvidia/segformer-b0-finetuned-ade-512-512
- ONNX export: https://huggingface.co/optimum/segformer-b0-finetuned-ade-512-512
- Pinned export revision: `bc01a2c52665fff5002aa58ec18381ae7444b4f2`
- Download: https://huggingface.co/optimum/segformer-b0-finetuned-ade-512-512/resolve/bc01a2c52665fff5002aa58ec18381ae7444b4f2/model.onnx
- SHA-256: `3a89102115fe3c16230502437b894844ba50cde6f7c800f9884e87c360bcbfc9`
- Size: 15,142,812 bytes
- License: NVIDIA Source Code License for SegFormer, included in `LICENSE.txt`. Research/evaluation, non-commercial use only.
- Source repository: https://github.com/NVlabs/SegFormer

Checkpoint training resolution: 512x512. Mobile inference uses dynamic 256x256 inputs
to keep the complete camera analysis within the result freshness budget.
Input: float32 RGB NCHW `[1,3,256,256]`, bilinear resize, RGB / 255,
mean `[.485,.456,.406]`, std `[.229,.224,.225]`.
Output: float32 logits `[1,150,64,64]`. Output indices already match `config.json`;
the processor's reduce-labels setting applies to training annotation maps only.

Walkable output classes: floor (3), grass (9), sidewalk (11), earth (13), rug (28), path (52).
Road (6), stairs (53/59), furniture, walls, and all other classes are blocked.
Winning-class softmax must be >= .55. The center and at least 6/9 neighboring pixels must pass;
the 12cm footprint requires its center and at least 4/5 sample points to pass.
ARCore still provides world tracking, floor polygons/heights, and vertical wall collision checks.
Inference runs serially on two CPU threads, at most 4 starts/sec; no queued camera frames.
Results expire 1.5 seconds after capture and are invalidated on tracking loss.
Obstacle detection is the default. Only a 35cm half-width corridor along the actual traversed
user path overrides it as trusted ground; nearby untraveled ground is never auto-approved. Trusted ground
overrides semantic obstacles, geometric walls and step-height restrictions. It is also shown green
in the overlay. Trail history is session-local and cleared on tracking loss or a >80cm pose jump.
Before the first floor observation, ground height is estimated at cameraY - 1.35m; the first real
floor observation replaces that estimate. A different established ground level requires three
distinct AR plane refreshes. Queries prefer the established ground level instead of switching
between overlapping floor/table planes as camera height changes.
Outside the traversed corridor, normal walking tries bounded A* (12cm cells, 1.5m radius, <=500
expansions) when a wall, missing floor or step-height discontinuity blocks motion. Edges require
verified floor and collision clearance; diagonal corner cutting is rejected. No complete local
route means stop. Walking through a previously blocked region promotes the trail and reopens it.
The chosen path is kept until the direct route has been clear for .3s; failed searches retry at
most every .3s. Forced 2m return and leash recall retain their explicit obstacle-ignoring behavior.
Diagnostic floor colors reproject world points into the model's captured camera pose. Other
regions compensate camera rotation; their translation cannot be fully corrected without depth.
Verified 15cm world cells are remembered for up to 3s without extending expiry
on missing observations. Fresh obstacle evidence at the same cell must span >= .3s across distinct
model captures; confirmed evidence invalidates old clear-floor memory. Geometric walls still block
immediately, using a 24cm body radius. Tracking loss clears the world-floor cache.
Cached/uncertain floor limits actual movement to .35m/s. A previously unmapped gap gets at most
.3s and 15cm of creep from the last verified point; uncertain areas never permit initial placement.
The overlay reports stop episode counts/durations. `adb logcat -s ArWalkStops` shows the per-reason
breakdown every 5s, including frame delay, wall/route, height, unknown floor and tracking loss.
Explicit/automatic return continues to bypass obstacle and floor restrictions.

Validation: `gradlew.bat :app:testDebugUnitTest` checks the model hash, a real ONNX forward pass,
class/confidence policy, world/image projection, rotation, and stale result rejection.
Device latency and indoor recognition quality must also be checked on the target phone.
Device diagnostic: build `:app:assembleDebugAndroidTest`, install the test APK, and run
`adb shell am instrument -w com.usee.animal.test/com.usee.animal.ar.SegFormerDeviceCheck`.
This runs the installed app's YUV preprocessing and native ONNX inference at 512 and 256 pixels.
