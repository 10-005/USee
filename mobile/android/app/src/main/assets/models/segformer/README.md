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
Winning-class softmax must be >= .55; a 3x3 neighborhood and a 15cm footprint are checked.
ARCore still provides world tracking, floor polygons/heights, and vertical wall collision checks.
Inference runs serially on two CPU threads, at most 4 starts/sec; no queued camera frames.
Results expire 1.5 seconds after capture and are invalidated on tracking loss.
No plane-only fallback: loading/stale results pause movement; inference failure reports an error.

Validation: `gradlew.bat :app:testDebugUnitTest` checks the model hash, a real ONNX forward pass,
class/confidence policy, world/image projection, rotation, and stale result rejection.
Device latency and indoor recognition quality must also be checked on the target phone.
Device diagnostic: build `:app:assembleDebugAndroidTest`, install the test APK, and run
`adb shell am instrument -w com.usee.animal.test/com.usee.animal.ar.SegFormerDeviceCheck`.
This runs the installed app's YUV preprocessing and native ONNX inference at 512 and 256 pixels.
