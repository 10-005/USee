"""Device adapter; original recognition geometry and vendor sources are unchanged."""
import json
import numpy as np
from common import validate_device
from pet_pipeline.core import Pipeline, PipelineConfig
from pet_pipeline.assets import ROOT as CAT_ROOT, verify
from pet_pipeline.runtime import install_vendor_path


class CpuSession:
    def __init__(self, path):
        import onnxruntime as ort
        options = ort.SessionOptions()
        options.intra_op_num_threads = 4
        options.inter_op_num_threads = 1
        options.log_severity_level = 3
        self.session = ort.InferenceSession(str(path), sess_options=options, providers=['CPUExecutionProvider'])
        self.session.disable_fallback()
        self.inputs = [i.name for i in self.session.get_inputs()]
        self.outputs = [i.name for i in self.session.get_outputs()]

    def run(self, **inputs):
        if set(inputs) != set(self.inputs):
            raise ValueError('ONNX input mismatch')
        return dict(zip(self.outputs, self.session.run(self.outputs, {k: np.ascontiguousarray(v) for k, v in inputs.items()})))

class CatEncoder(Pipeline):
    def __init__(self, device='cpu'):
        self.device = validate_device(device)
        if device == 'cuda:1':
            super().__init__()
            return
        # Reuse release _recognize, grouping, letterbox, alignment and transforms.
        self.config = PipelineConfig()
        self.provenance = verify()
        install_vendor_path()
        from cat_recognition.deployment.preprocess import build_pose_transform, build_recognition_transform
        self.manifest = json.loads((CAT_ROOT / 'models/MeowID-Base/deployment.json').read_text())
        p = self.manifest['preprocessing']
        self.pose_transform = build_pose_transform(tuple(p['pose']['image_size']), tuple(p['pose']['mean']), tuple(p['pose']['std']))
        for name in ['body', 'face']:
            c = p[name]
            setattr(self, name + '_transform', build_recognition_transform(c['image_size'], c['resize_size'], tuple(c['mean']), tuple(c['std'])))
        self.pose = CpuSession(CAT_ROOT / 'models/MeowID-Base/ecpose.onnx')
        self.recognition = CpuSession(CAT_ROOT / 'models/MeowID-Base/meowid_base.onnx')
        self.runtime = {'device': 'cpu', 'adapter': 'explicit-local-cpu-v1'}

    def process(self, source):
        outputs = super().process(source)
        for output in outputs:
            output.metadata['device'] = self.device
        return outputs
