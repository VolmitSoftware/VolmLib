const GRAD2: [f64; 16] = [
    -1., -1., 1., -1., -1., 1., 1., 1., 0., -1., -1., 0., 0., 1., 1., 0.,
];
const GRAD3: [f64; 48] = [
    1., 1., 0., -1., 1., 0., 1., -1., 0., -1., -1., 0., 1., 0., 1., -1., 0., 1., 1., 0., -1., -1.,
    0., -1., 0., 1., 1., 0., -1., 1., 0., 1., -1., 0., -1., -1., 1., 1., 0., 0., -1., 1., -1., 1.,
    0., 0., -1., -1.,
];
const F2: f64 = 0.3660254037844386;
const G2: f64 = 0.21132486540518713;
const F3: f64 = 1.0 / 3.0;
const G3: f64 = 1.0 / 6.0;
const G33: f64 = G3 * 3.0 - 1.0;

#[inline(always)]
fn hash(seed: i64, x: i64, y: i64, z: i64) -> i64 {
    let mut h: i64 = seed ^ x.wrapping_mul(1619) ^ y.wrapping_mul(31337) ^ z.wrapping_mul(6971);
    h = h.wrapping_mul(h).wrapping_mul(h).wrapping_mul(60493);
    (h >> 13) ^ h
}

#[inline(always)]
fn contribution2(seed: i64, i: i64, j: i64, x: f64, y: f64) -> f64 {
    let mut t: f64 = 0.5 - x * x - y * y;
    if t < 0.0 {
        return 0.0;
    }
    t *= t;
    let gi: usize = ((hash(seed, i, j, 0) & 7) << 1) as usize;
    t * t * (x * GRAD2[gi] + y * GRAD2[gi + 1])
}

#[inline(always)]
fn simplex2(seed: i64, x: f64, y: f64) -> f64 {
    let mut t: f64 = (x + y) * F2;
    let i: i64 = (x + t).floor() as i64;
    let j: i64 = (y + t).floor() as i64;
    t = i.wrapping_add(j) as f64 * G2;
    let x0: f64 = x - (i as f64 - t);
    let y0: f64 = y - (j as f64 - t);
    let (i1, j1): (i64, i64) = if x0 > y0 { (1, 0) } else { (0, 1) };
    let x1: f64 = x0 - i1 as f64 + G2;
    let y1: f64 = y0 - j1 as f64 + G2;
    let x2: f64 = x0 - 1.0 + (2.0 * G2);
    let y2: f64 = y0 - 1.0 + (2.0 * G2);
    let n0: f64 = contribution2(seed, i, j, x0, y0);
    let n1: f64 = contribution2(seed, i.wrapping_add(i1), j.wrapping_add(j1), x1, y1);
    let n2: f64 = contribution2(seed, i.wrapping_add(1), j.wrapping_add(1), x2, y2);
    50.0 * (n0 + n1 + n2)
}

#[inline(always)]
fn contribution3(seed: i64, ijk: [i64; 3], xyz: [f64; 3]) -> f64 {
    let [x, y, z]: [f64; 3] = xyz;
    let mut t: f64 = 0.6 - x * x - y * y - z * z;
    if t < 0.0 {
        return 0.0;
    }
    t *= t;
    let gi: usize = (hash(seed, ijk[0], ijk[1], ijk[2]) & 15) as usize * 3;
    t * t * (x * GRAD3[gi] + y * GRAD3[gi + 1] + z * GRAD3[gi + 2])
}

#[inline(always)]
fn simplex3(seed: i64, x: f64, y: f64, z: f64) -> f64 {
    let mut t: f64 = (x + y + z) * F3;
    let i: i64 = (x + t).floor() as i64;
    let j: i64 = (y + t).floor() as i64;
    let k: i64 = (z + t).floor() as i64;
    t = i.wrapping_add(j).wrapping_add(k) as f64 * G3;
    let x0: f64 = x - (i as f64 - t);
    let y0: f64 = y - (j as f64 - t);
    let z0: f64 = z - (k as f64 - t);
    let (a, b): ([i64; 3], [i64; 3]) = if x0 >= y0 {
        if y0 >= z0 {
            ([1, 0, 0], [1, 1, 0])
        } else if x0 >= z0 {
            ([1, 0, 0], [1, 0, 1])
        } else {
            ([0, 0, 1], [1, 0, 1])
        }
    } else {
        if y0 < z0 {
            ([0, 0, 1], [0, 1, 1])
        } else if x0 < z0 {
            ([0, 1, 0], [0, 1, 1])
        } else {
            ([0, 1, 0], [1, 1, 0])
        }
    };
    let p1: [f64; 3] = [
        x0 - a[0] as f64 + G3,
        y0 - a[1] as f64 + G3,
        z0 - a[2] as f64 + G3,
    ];
    let p2: [f64; 3] = [
        x0 - b[0] as f64 + F3,
        y0 - b[1] as f64 + F3,
        z0 - b[2] as f64 + F3,
    ];
    let n0: f64 = contribution3(seed, [i, j, k], [x0, y0, z0]);
    let n1: f64 = contribution3(
        seed,
        [
            i.wrapping_add(a[0]),
            j.wrapping_add(a[1]),
            k.wrapping_add(a[2]),
        ],
        p1,
    );
    let n2: f64 = contribution3(
        seed,
        [
            i.wrapping_add(b[0]),
            j.wrapping_add(b[1]),
            k.wrapping_add(b[2]),
        ],
        p2,
    );
    let n3: f64 = contribution3(
        seed,
        [i.wrapping_add(1), j.wrapping_add(1), k.wrapping_add(1)],
        [x0 + G33, y0 + G33, z0 + G33],
    );
    32.0 * (n0 + n1 + n2 + n3)
}

#[unsafe(no_mangle)]
pub extern "C" fn noise_scalar(
    seed: i64,
    x: f64,
    y: f64,
    z: f64,
    dim: i32,
    octaves: i32,
    bounding: f64,
) -> f64 {
    if octaves == 1 {
        return if dim == 2 {
            simplex2(seed, x * 0.01, z * 0.01)
        } else {
            simplex3(seed, x * 0.01, y * 0.01, z * 0.01)
        };
    }
    let mut frequency: f64 = 1.0;
    let mut amplitude: f64 = 1.0;
    let mut value: f64 = 0.0;
    for _ in 0..octaves {
        value += if dim == 2 {
            simplex2(seed, (x * frequency) * 0.01, (z * frequency) * 0.01) * amplitude
        } else {
            simplex3(
                seed,
                (x * frequency) * 0.01,
                (y * frequency) * 0.01,
                (z * frequency) * 0.01,
            ) * amplitude
        };
        frequency *= 2.0;
        amplitude *= 0.5;
    }
    value * bounding
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn noise_batch(
    seed: i64,
    xyz: *const f64,
    out: *mut f64,
    count: i32,
    dim: i32,
    octaves: i32,
    bounding: f64,
) {
    for n in 0..count as usize {
        unsafe {
            *out.add(n) = noise_scalar(
                seed,
                *xyz.add(n * 3),
                *xyz.add(n * 3 + 1),
                *xyz.add(n * 3 + 2),
                dim,
                octaves,
                bounding,
            );
        }
    }
}

#[unsafe(no_mangle)]
pub extern "C" fn billow_scalar(
    mut seed: i64,
    mut x: f64,
    mut y: f64,
    mut z: f64,
    dim: i32,
    octaves: i32,
    bounding: f64,
) -> f64 {
    x *= 0.01;
    y *= 0.01;
    z *= 0.01;
    let mut sum: f64 = if dim == 2 {
        simplex2(seed, x, z).abs() * 2.0 - 1.0
    } else {
        simplex3(seed, x, y, z).abs() * 2.0 - 1.0
    };
    let mut amplitude: f64 = 1.0;
    for _ in 1..octaves {
        x *= 2.0;
        y *= 2.0;
        z *= 2.0;
        amplitude *= 0.5;
        seed = seed.wrapping_add(1);
        sum += if dim == 2 {
            (simplex2(seed, x, z).abs() * 2.0 - 1.0) * amplitude
        } else {
            (simplex3(seed, x, y, z).abs() * 2.0 - 1.0) * amplitude
        };
    }
    (sum * bounding) / 2.0 + 0.5
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn billow_batch(
    seed: i64,
    xyz: *const f64,
    out: *mut f64,
    count: i32,
    dim: i32,
    octaves: i32,
    bounding: f64,
) {
    for n in 0..count as usize {
        unsafe {
            *out.add(n) = billow_scalar(
                seed,
                *xyz.add(n * 3),
                *xyz.add(n * 3 + 1),
                *xyz.add(n * 3 + 2),
                dim,
                octaves,
                bounding,
            );
        }
    }
}
