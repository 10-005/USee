// Preserve the edited mesh/animations and add a leash socket on the animated neck.
const fs = require('fs');
const path = require('path');
const source = process.argv[2] || path.resolve(__dirname, '../../artifacts/dog-blue-collar/dog-blue-collar.glb');
const destination = path.resolve(__dirname, '../android/app/src/main/assets/models/dog.glb');
const bytes = fs.readFileSync(source);
const jsonLength = bytes.readUInt32LE(12);
const model = JSON.parse(bytes.subarray(20, 20 + jsonLength));
const binary = bytes.subarray(28 + jsonLength);
const collar = model.nodes.find(node => node.name === 'BlueCollar');
const neck = model.nodes.findIndex(node => node.name === 'neck');
if (!collar || neck < 0 || model.animations.length !== 74) throw Error('Unexpected collar model');
const skin = model.skins[collar.skin];
const joint = skin.joints.indexOf(neck);
if (joint < 0) throw Error('Collar is not attached to neck');
function floats(accessorIndex, count, element = 0) {
  const accessor = model.accessors[accessorIndex];
  const view = model.bufferViews[accessor.bufferView];
  const offset = (view.byteOffset || 0) + (accessor.byteOffset || 0) + element * (view.byteStride || count * 4);
  return Array.from({ length: count }, (_, i) => binary.readFloatLE(offset + i * 4));
}
const primitive = model.meshes[collar.mesh].primitives[0];
const positions = model.accessors[primitive.attributes.POSITION];
let dorsal, left, right;
for (let i = 0; i < positions.count; i++) {
  const p = floats(primitive.attributes.POSITION, 3, i);
  if (Math.abs(p[0]) < 0.004 && (!dorsal || p[1] > dorsal[1])) dorsal = p;
  if (!left || p[0] < left[0]) left = p;
  if (!right || p[0] > right[0]) right = p;
}
if (!dorsal) throw Error('No dorsal collar vertex');
// Put the socket just outside the band, then convert mesh bind space to neck-local space.
dorsal[0] = 0;
dorsal[1] += 0.003;
const inverseBind = floats(skin.inverseBindMatrices, 16, joint);
left[0] -= 0.015;
right[0] += 0.015;
for (const [name, point] of [['LeashAttachment', dorsal], ['LeashAttachmentLeft', left], ['LeashAttachmentRight', right]]) {
  const local = [0, 1, 2].map(row => inverseBind[row] * point[0] + inverseBind[4 + row] * point[1] + inverseBind[8 + row] * point[2] + inverseBind[12 + row]);
  const socket = model.nodes.length;
  model.nodes.push({ name, translation: local });
  model.nodes[neck].children = [...(model.nodes[neck].children || []), socket];
}
let json = Buffer.from(JSON.stringify(model));
json = Buffer.concat([json, Buffer.alloc((4 - json.length % 4) % 4, 32)]);
const header = Buffer.alloc(20);
header.write('glTF'); header.writeUInt32LE(2, 4);
header.writeUInt32LE(28 + json.length + binary.length, 8);
header.writeUInt32LE(json.length, 12); header.write('JSON', 16);
const binaryHeader = Buffer.alloc(8);
binaryHeader.writeUInt32LE(binary.length); binaryHeader.writeUInt32LE(0x004e4942, 4);
fs.writeFileSync(destination, Buffer.concat([header, json, binaryHeader, binary]));
console.log(JSON.stringify({ animations: model.animations.length, sockets: 3, socketBindPosition: dorsal }));
