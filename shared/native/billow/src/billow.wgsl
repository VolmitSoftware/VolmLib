struct Lane {
    packed_i: i32,
    j: i32,
    x0: f32,
    z0: f32,
}

struct Params {
    seed: u32,
    count: u32,
    octaves: u32,
    bounding: f32,
}

@group(0) @binding(0) var<storage, read> lanes: array<Lane>;
@group(0) @binding(1) var<storage, read_write> output: array<vec2<f32>>;
@group(0) @binding(2) var<uniform> params: Params;

fn gradient(seed: u32, x: u32, z: u32, xd: f32, zd: f32) -> f32 {
    var hash: u32 = seed ^ (1619u * x) ^ (31337u * z);
    hash = hash * hash * hash * 60493u;
    hash = (hash >> 13u) ^ hash;
    let gradients: array<vec2<f32>, 8> = array<vec2<f32>, 8>(
        vec2<f32>(-1.0, -1.0), vec2<f32>(1.0, -1.0),
        vec2<f32>(-1.0, 1.0), vec2<f32>(1.0, 1.0),
        vec2<f32>(0.0, -1.0), vec2<f32>(-1.0, 0.0),
        vec2<f32>(0.0, 1.0), vec2<f32>(1.0, 0.0)
    );
    let direction: vec2<f32> = gradients[hash & 7u];
    return xd * direction.x + zd * direction.y;
}

fn contribution(seed: u32, i: u32, j: u32, x: f32, z: f32) -> f32 {
    var t: f32 = 0.5 - x * x - z * z;
    if t < 0.0 {
        return 0.0;
    }
    t = t * t;
    return t * t * gradient(seed, i, j, x, z);
}

fn single(seed: u32, lane: Lane) -> f32 {
    let i: u32 = bitcast<u32>(lane.packed_i >> 1u);
    let j: u32 = bitcast<u32>(lane.j);
    let i1: u32 = bitcast<u32>(lane.packed_i) & 1u;
    let j1: u32 = 1u - i1;
    let g2: f32 = 0.21132486540518713;
    let x1: f32 = lane.x0 - f32(i1) + g2;
    let z1: f32 = lane.z0 - f32(j1) + g2;
    let x2: f32 = lane.x0 - 1.0 + 2.0 * g2;
    let z2: f32 = lane.z0 - 1.0 + 2.0 * g2;
    let n0: f32 = contribution(seed, i, j, lane.x0, lane.z0);
    let n1: f32 = contribution(seed, i + i1, j + j1, x1, z1);
    let n2: f32 = contribution(seed, i + 1u, j + 1u, x2, z2);
    return 50.0 * (n0 + n1 + n2);
}

@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) invocation: vec3<u32>) {
    let id: u32 = invocation.x;
    if id >= params.count {
        return;
    }
    var seed: u32 = params.seed;
    var sum: f32 = abs(single(seed, lanes[id])) * 2.0 - 1.0;
    var amplitude: f32 = 1.0;
    for (var octave: u32 = 1u; octave < params.octaves; octave = octave + 1u) {
        amplitude = amplitude * 0.5;
        seed = seed + 1u;
        let value: f32 = single(seed, lanes[octave * params.count + id]);
        sum = sum + (abs(value) * 2.0 - 1.0) * amplitude;
    }
    let raw_signed: f32 = sum * params.bounding;
    let unsigned_value: f32 = raw_signed / 2.0 + 0.5;
    output[id] = vec2<f32>(unsigned_value, unsigned_value * 2.0 - 1.0);
}
