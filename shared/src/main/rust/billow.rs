const GRAD2: [f64; 16] = [-1., -1., 1., -1., -1., 1., 1., 1., 0., -1., -1., 0., 0., 1., 1., 0.];
const F2: f64 = 0.3660254037844386;
const G2: f64 = 0.21132486540518713;

#[inline(always)]
fn hash(seed: i64, x: i64, y: i64, z: i64) -> i64 {
    let mut h: i64 = seed ^ x.wrapping_mul(1619) ^ y.wrapping_mul(31337) ^ z.wrapping_mul(6971);
    h = h.wrapping_mul(h).wrapping_mul(h).wrapping_mul(60493);
    (h >> 13) ^ h
}

#[inline(always)]
fn contribution2(seed: i64, i: i64, j: i64, x: f64, y: f64) -> f64 {
    let mut t: f64 = 0.5 - x * x - y * y;
    if t < 0.0 { return 0.0; }
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
    let (i1, j1): (i64, i64) = if x0 > y0 { (1,0) } else { (0,1) };
    let x1: f64 = x0 - i1 as f64 + G2;
    let y1: f64 = y0 - j1 as f64 + G2;
    let x2: f64 = x0 - 1.0 + (2.0 * G2);
    let y2: f64 = y0 - 1.0 + (2.0 * G2);
    let n0: f64 = contribution2(seed, i, j, x0, y0);
    let n1: f64 = contribution2(seed, i.wrapping_add(i1), j.wrapping_add(j1), x1, y1);
    let n2: f64 = contribution2(seed, i.wrapping_add(1), j.wrapping_add(1), x2, y2);
    50.0 * (n0 + n1 + n2)
}


#[unsafe(no_mangle)]
pub extern "C" fn volmlib_billow2d_v1(mut seed: i64, mut x: f64, mut z: f64, octaves: i32, bounding: f64) -> f64 {
    x *= 0.01;
    z *= 0.01;
    let mut sum: f64 = simplex2(seed, x, z).abs() * 2.0 - 1.0;
    let mut amplitude: f64 = 1.0;
    for _ in 1..octaves {
        x *= 2.0;
        z *= 2.0;
        amplitude *= 0.5;
        seed = seed.wrapping_add(1);
        sum += (simplex2(seed, x, z).abs() * 2.0 - 1.0) * amplitude;
    }
    (sum * bounding) / 2.0 + 0.5
}
