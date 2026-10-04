mod cpu;

use std::collections::HashMap;
use std::sync::atomic::AtomicBool;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex, OnceLock};
use std::time::Duration;

#[repr(C)]
#[derive(Clone, Copy, bytemuck::Pod, bytemuck::Zeroable)]
struct Lane {
    packed_i: i32,
    j: i32,
    x: f32,
    z: f32,
}
#[repr(C)]
#[derive(Clone, Copy, bytemuck::Pod, bytemuck::Zeroable)]
struct Parameters {
    seed: u32,
    count: u32,
    octaves: u32,
    bounding: f32,
}
struct Buffers {
    input: wgpu::Buffer,
    uniform: wgpu::Buffer,
    result: wgpu::Buffer,
    readback: wgpu::Buffer,
    capacity: usize,
}
struct Context {
    device: wgpu::Device,
    queue: wgpu::Queue,
    pipeline: wgpu::ComputePipeline,
    failed: bool,
    lost: Arc<AtomicBool>,
    buffers: Option<Buffers>,
    lanes: Vec<Lane>,
    corrections: usize,
    converted: Vec<f64>,
}
static REGISTRY: OnceLock<Mutex<HashMap<u64, Arc<Mutex<Context>>>>> = OnceLock::new();
static NEXT: AtomicU64 = AtomicU64::new(1);
fn registry() -> &'static Mutex<HashMap<u64, Arc<Mutex<Context>>>> {
    REGISTRY.get_or_init(|| Mutex::new(HashMap::new()))
}
fn bounding(octaves: i32) -> f64 {
    let mut sum: f64 = 1.;
    let mut amplitude: f64 = 0.5;
    for _ in 1..octaves {
        sum += amplitude;
        amplitude *= 0.5;
    }
    1. / sum
}

fn eligible_adapter(name: &str, device_type: wgpu::DeviceType, preference: &str) -> bool {
    device_type != wgpu::DeviceType::Cpu && name.to_lowercase().contains(preference)
}

impl Context {
    fn create() -> Option<Self> {
        Self::create_shader(include_str!("billow.wgsl"))
    }
    fn create_shader(source: &str) -> Option<Self> {
        let instance: wgpu::Instance =
            wgpu::Instance::new(wgpu::InstanceDescriptor::new_without_display_handle_from_env());
        let mut adapters: Vec<wgpu::Adapter> =
            pollster::block_on(instance.enumerate_adapters(wgpu::Backends::PRIMARY));
        let preference: String = std::env::var("VOLMLIB_GPU_ADAPTER")
            .unwrap_or_default()
            .to_lowercase();
        adapters.retain(|adapter| {
            let info: wgpu::AdapterInfo = adapter.get_info();
            eligible_adapter(&info.name, info.device_type, &preference)
        });
        adapters.sort_by_key(|adapter| match adapter.get_info().device_type {
            wgpu::DeviceType::DiscreteGpu => 0,
            wgpu::DeviceType::IntegratedGpu => 1,
            _ => 2,
        });
        for adapter in adapters {
            if !adapter
                .get_downlevel_capabilities()
                .flags
                .contains(wgpu::DownlevelFlags::COMPUTE_SHADERS)
            {
                continue;
            }
            let mut limits: wgpu::Limits = wgpu::Limits::default();
            limits.max_storage_buffer_binding_size = adapter
                .limits()
                .max_storage_buffer_binding_size
                .min(1048576 * 9 * 16);
            let Ok((device, queue)) =
                pollster::block_on(adapter.request_device(&wgpu::DeviceDescriptor {
                    label: Some("VolmLib Billow"),
                    required_limits: limits,
                    ..Default::default()
                }))
            else {
                continue;
            };
            let scope = device.push_error_scope(wgpu::ErrorFilter::Validation);
            let shader: wgpu::ShaderModule =
                device.create_shader_module(wgpu::ShaderModuleDescriptor {
                    label: Some("Billow"),
                    source: wgpu::ShaderSource::Wgsl(source.into()),
                });
            let pipeline: wgpu::ComputePipeline =
                device.create_compute_pipeline(&wgpu::ComputePipelineDescriptor {
                    label: Some("Billow"),
                    layout: None,
                    module: &shader,
                    entry_point: Some("main"),
                    compilation_options: Default::default(),
                    cache: None,
                });
            if pollster::block_on(scope.pop()).is_some() {
                continue;
            }
            let lost = Arc::new(AtomicBool::new(false));
            let lost_handler = lost.clone();
            device.set_device_lost_callback(move |_, _| {
                lost_handler.store(true, Ordering::Relaxed);
            });
            let mut context: Self = Self {
                device,
                queue,
                pipeline,
                failed: false,
                lost,
                buffers: None,
                lanes: Vec::new(),
                corrections: 0,
                converted: Vec::new(),
            };
            if context.validate() {
                eprintln!(
                    "VolmLib GPU adapter={} backend={:?}",
                    adapter.get_info().name,
                    adapter.get_info().backend
                );
                return Some(context);
            }
        }
        None
    }
    fn fill(&mut self, seed: i64, octaves: i32, xyz: &[f64]) -> Option<()> {
        if self.failed || self.lost.load(Ordering::Relaxed) || !(octaves == 8 || octaves == 9) {
            return None;
        }
        let count: usize = xyz.len() / 3;
        let lane_bytes: usize = count.checked_mul(9)?.checked_mul(16)?;
        let limits: wgpu::Limits = self.device.limits();
        if count == 0
            || count > 1048576
            || lane_bytes > limits.max_storage_buffer_binding_size as usize
            || lane_bytes as u64 > limits.max_buffer_size
            || count.div_ceil(64) > limits.max_compute_workgroups_per_dimension as usize
        {
            return None;
        }
        self.lanes.resize(
            count * octaves as usize,
            Lane {
                packed_i: 0,
                j: 0,
                x: 0.,
                z: 0.,
            },
        );
        for index in 0..count {
            let mut x: f64 = xyz[index * 3] * 0.01;
            let mut z: f64 = xyz[index * 3 + 2] * 0.01;
            for octave in 0..octaves as usize {
                let skew: f64 = (x + z) * 0.3660254037844386;
                let i: f64 = (x + skew).floor();
                let j: f64 = (z + skew).floor();
                if !i.is_finite()
                    || !j.is_finite()
                    || !(-1073741824. ..=1073741823.).contains(&i)
                    || !(i32::MIN as f64..=i32::MAX as f64).contains(&j)
                {
                    return None;
                }
                let unskew: f64 = (i as i64 + j as i64) as f64 * 0.21132486540518713;
                let x0: f64 = x - (i - unskew);
                let z0: f64 = z - (j - unskew);
                self.lanes[octave * count + index] = Lane {
                    packed_i: ((i as i32 as u32) << 1 | u32::from(x0 > z0)) as i32,
                    j: j as i32,
                    x: x0 as f32,
                    z: z0 as f32,
                };
                x *= 2.;
                z *= 2.;
            }
        }
        let normal: f64 = bounding(octaves);
        let parameters: Parameters = Parameters {
            seed: seed as u32,
            count: count as u32,
            octaves: octaves as u32,
            bounding: normal as f32,
        };
        if self
            .buffers
            .as_ref()
            .is_none_or(|buffers| buffers.capacity < count)
        {
            let create = |size: u64, usage: wgpu::BufferUsages| {
                self.device.create_buffer(&wgpu::BufferDescriptor {
                    label: None,
                    size,
                    usage,
                    mapped_at_creation: false,
                })
            };
            self.buffers = Some(Buffers {
                input: create(
                    count as u64 * 9 * 16,
                    wgpu::BufferUsages::STORAGE | wgpu::BufferUsages::COPY_DST,
                ),
                uniform: create(
                    16,
                    wgpu::BufferUsages::UNIFORM | wgpu::BufferUsages::COPY_DST,
                ),
                result: create(
                    count as u64 * 8,
                    wgpu::BufferUsages::STORAGE | wgpu::BufferUsages::COPY_SRC,
                ),
                readback: create(
                    count as u64 * 8,
                    wgpu::BufferUsages::MAP_READ | wgpu::BufferUsages::COPY_DST,
                ),
                capacity: count,
            });
        }
        let buffers = self.buffers.as_ref()?;
        let input = &buffers.input;
        let uniform = &buffers.uniform;
        let result = &buffers.result;
        let readback = &buffers.readback;
        self.queue
            .write_buffer(input, 0, bytemuck::cast_slice(&self.lanes));
        self.queue
            .write_buffer(uniform, 0, bytemuck::bytes_of(&parameters));
        let size: u64 = count as u64 * 8;
        let group: wgpu::BindGroup = self.device.create_bind_group(&wgpu::BindGroupDescriptor {
            label: None,
            layout: &self.pipeline.get_bind_group_layout(0),
            entries: &[
                wgpu::BindGroupEntry {
                    binding: 0,
                    resource: input.as_entire_binding(),
                },
                wgpu::BindGroupEntry {
                    binding: 1,
                    resource: result.as_entire_binding(),
                },
                wgpu::BindGroupEntry {
                    binding: 2,
                    resource: uniform.as_entire_binding(),
                },
            ],
        });
        let mut encoder: wgpu::CommandEncoder =
            self.device.create_command_encoder(&Default::default());
        {
            let mut pass: wgpu::ComputePass = encoder.begin_compute_pass(&Default::default());
            pass.set_pipeline(&self.pipeline);
            pass.set_bind_group(0, &group, &[]);
            pass.dispatch_workgroups(count.div_ceil(64) as u32, 1, 1);
        }
        encoder.copy_buffer_to_buffer(&result, 0, &readback, 0, size);
        self.queue.submit([encoder.finish()]);
        let (sender, receiver) = std::sync::mpsc::sync_channel(1);
        readback
            .slice(..size)
            .map_async(wgpu::MapMode::Read, move |status| {
                let _ = sender.send(status);
            });
        if self
            .device
            .poll(wgpu::PollType::Wait {
                submission_index: None,
                timeout: Some(Duration::from_secs(30)),
            })
            .is_err()
            || !matches!(receiver.recv_timeout(Duration::from_secs(1)), Ok(Ok(())))
        {
            self.failed = true;
            return None;
        }
        let mapped = match readback.slice(..size).get_mapped_range() {
            Ok(mapped) => mapped,
            Err(_) => {
                self.failed = true;
                readback.unmap();
                return None;
            }
        };
        let raw: &[[f32; 2]] = bytemuck::cast_slice(&mapped);
        self.converted.clear();
        self.converted.reserve(count);
        self.corrections = 0;
        for (index, value) in raw.iter().enumerate() {
            let mut unsigned: f64 = value[0] as f64;
            if !unsigned.is_finite()
                || !value[1].is_finite()
                || unsigned < 0.001
                || value[1].abs() < 0.001
            {
                self.corrections += 1;
                unsigned = cpu::billow_scalar(
                    seed,
                    xyz[index * 3],
                    0.,
                    xyz[index * 3 + 2],
                    2,
                    octaves,
                    normal,
                );
            }
            if !unsigned.is_finite() {
                drop(mapped);
                readback.unmap();
                return None;
            }
            self.converted.push(unsigned);
        }
        drop(mapped);
        readback.unmap();
        Some(())
    }
    fn validate(&mut self) -> bool {
        let mut xyz: Vec<f64> = vec![0.; 256 * 3];
        let mut state: u64 = 0x817A52648372D819;
        for index in 0..256 {
            for offset in [0, 2] {
                state = state
                    .wrapping_mul(6364136223846793005)
                    .wrapping_add(1442695040888963407);
                xyz[index * 3 + offset] =
                    (state >> 11) as f64 * (1. / 9007199254740992.) * 60000000. - 30000000.;
            }
        }
        xyz[0] = 0.;
        xyz[2] = 0.;
        xyz[3] = 1e-14;
        xyz[5] = -1e-14;
        for octaves in [8, 9] {
            for seed in [0, -77, i64::MAX, 2950183808951183360] {
                let Some(()) = self.fill(seed, octaves, &xyz) else {
                    return false;
                };
                if self.corrections > 14 {
                    return false;
                }
                for index in 0..256 {
                    let expected: f64 = cpu::billow_scalar(
                        seed,
                        xyz[index * 3],
                        0.,
                        xyz[index * 3 + 2],
                        2,
                        octaves,
                        bounding(octaves),
                    );
                    let delta: f64 = (self.converted[index] - expected).abs();
                    if delta > expected.abs() * 0.05
                        || delta * 2. > (expected * 2. - 1.).abs() * 0.05
                    {
                        return false;
                    }
                }
            }
        }
        true
    }
}
#[unsafe(no_mangle)]
pub extern "C" fn iris_gpu_create() -> u64 {
    std::panic::catch_unwind(|| {
        let Some(context) = Context::create() else {
            return 0;
        };
        let handle: u64 = NEXT.fetch_add(1, Ordering::Relaxed);
        registry()
            .lock()
            .unwrap()
            .insert(handle, Arc::new(Mutex::new(context)));
        handle
    })
    .unwrap_or(0)
}
#[unsafe(no_mangle)]
pub unsafe extern "C" fn iris_gpu_fill(
    handle: u64,
    seed: i64,
    octaves: i32,
    xyz: *const f64,
    output: *mut f64,
    count: i32,
) -> i32 {
    std::panic::catch_unwind(|| {
        if xyz.is_null() || output.is_null() || !(65536..=1048576).contains(&count) {
            return 0;
        }
        let context = registry().lock().unwrap().get(&handle).cloned();
        let Some(context) = context else {
            return 0;
        };
        let input: &[f64] = unsafe { std::slice::from_raw_parts(xyz, count as usize * 3) };
        let mut guard = context.lock().unwrap();
        let Some(()) = guard.fill(seed, octaves, input) else {
            return 0;
        };
        unsafe {
            std::ptr::copy_nonoverlapping(guard.converted.as_ptr(), output, count as usize);
        }
        1
    })
    .unwrap_or(0)
}
#[unsafe(no_mangle)]
pub extern "C" fn iris_gpu_destroy(handle: u64) {
    let _ = std::panic::catch_unwind(|| {
        registry().lock().unwrap().remove(&handle);
    });
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn software_adapters_are_never_gpu_candidates() {
        for name in ["llvmpipe", "Microsoft Basic Render Driver"] {
            assert!(!eligible_adapter(name, wgpu::DeviceType::Cpu, ""));
            assert!(!eligible_adapter(
                name,
                wgpu::DeviceType::Cpu,
                &name.to_lowercase()
            ));
        }
        for device_type in [
            wgpu::DeviceType::DiscreteGpu,
            wgpu::DeviceType::IntegratedGpu,
            wgpu::DeviceType::VirtualGpu,
            wgpu::DeviceType::Other,
        ] {
            assert!(eligible_adapter("Hardware adapter", device_type, ""));
            assert!(eligible_adapter(
                "Hardware adapter",
                device_type,
                "hardware"
            ));
            assert!(!eligible_adapter(
                "Hardware adapter",
                device_type,
                "llvmpipe"
            ));
        }
    }
    #[test]
    fn shader_translates_to_supported_backends() {
        let module: naga::Module =
            naga::front::wgsl::parse_str(include_str!("billow.wgsl")).unwrap();
        let info: naga::valid::ModuleInfo = naga::valid::Validator::new(
            naga::valid::ValidationFlags::all(),
            naga::valid::Capabilities::empty(),
        )
        .validate(&module)
        .unwrap();
        assert!(
            !naga::back::spv::write_vec(&module, &info, &Default::default(), None)
                .unwrap()
                .is_empty()
        );
        let options: naga::back::hlsl::Options = Default::default();
        let pipeline: naga::back::hlsl::PipelineOptions = Default::default();
        let mut hlsl: String = String::new();
        naga::back::hlsl::Writer::new(&mut hlsl, &options, &pipeline)
            .write(&module, &info, None)
            .unwrap();
        assert!(!hlsl.is_empty());
        let (msl, translation) =
            naga::back::msl::write_string(&module, &info, &Default::default(), &Default::default())
                .unwrap();
        assert!(!msl.is_empty());
        assert!(translation.entry_point_names.iter().all(Result::is_ok));
    }
    #[test]
    fn gpu_validation_and_handle_lifetime() {
        let handle: u64 = iris_gpu_create();
        if handle == 0 {
            return;
        }
        let count: usize = 65536;
        let mut xyz: Vec<f64> = vec![0.; count * 3];
        for index in 0..count {
            xyz[index * 3] = index as f64 * 1.125 - 30000.;
            xyz[index * 3 + 2] = index as f64 * -0.625 + 1000.;
        }
        let mut output: Vec<f64> = vec![0.; count];
        assert_eq!(
            unsafe {
                iris_gpu_fill(
                    handle,
                    -77,
                    9,
                    xyz.as_ptr(),
                    output.as_mut_ptr(),
                    count as i32,
                )
            },
            1
        );
        assert_eq!(
            unsafe { iris_gpu_fill(handle, -77, 9, xyz.as_ptr(), output.as_mut_ptr(), 1048577) },
            0
        );
        for index in 0..count {
            let expected: f64 = cpu::billow_scalar(
                -77,
                xyz[index * 3],
                0.,
                xyz[index * 3 + 2],
                2,
                9,
                bounding(9),
            );
            let delta: f64 = (output[index] - expected).abs();
            assert!(delta <= expected.abs() * 0.05);
            assert!(delta * 2. <= (expected * 2. - 1.).abs() * 0.05);
        }
        iris_gpu_destroy(handle);
        iris_gpu_destroy(handle);
        assert_eq!(
            unsafe {
                iris_gpu_fill(
                    handle,
                    -77,
                    9,
                    xyz.as_ptr(),
                    output.as_mut_ptr(),
                    count as i32,
                )
            },
            0
        );
    }
    #[test]
    fn destroyed_handle_during_fill_is_safe() {
        let handle = iris_gpu_create();
        if handle == 0 {
            return;
        }
        let context = registry().lock().unwrap().get(&handle).cloned().unwrap();
        let thread = std::thread::spawn(move || iris_gpu_destroy(handle));
        thread.join().unwrap();
        assert!(
            context
                .lock()
                .unwrap()
                .fill(0, 8, &[10., 0., -20.])
                .is_some()
        );
    }
    #[test]
    fn broken_shader_is_rejected() {
        let source = include_str!("billow.wgsl").replace(
            "output[id] = vec2<f32>(unsigned_value, unsigned_value * 2.0 - 1.0);",
            "output[id] = vec2<f32>(0.0, 0.0);",
        );
        assert!(Context::create_shader(&source).is_none());
    }
    #[test]
    fn device_loss_declines() {
        if let Some(mut context) = Context::create() {
            context.lost.store(true, Ordering::Relaxed);
            assert!(context.fill(0, 8, &[10., 0., -20.]).is_none());
        }
    }
    #[test]
    fn invalid_handle_and_nonfinite_decline() {
        assert_eq!(
            unsafe { iris_gpu_fill(0, 0, 8, std::ptr::null(), std::ptr::null_mut(), 65536) },
            0
        );
        if let Some(mut context) = Context::create() {
            assert!(context.fill(0, 8, &[f64::NAN, 0., 0.]).is_none());
        }
    }
}
