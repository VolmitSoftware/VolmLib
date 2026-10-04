fn main() {
    if std::env::var("CARGO_CFG_TARGET_OS").as_deref() == Ok("macos") {
        println!("cargo::rustc-cdylib-link-arg=-Wl,-fixup_chains");
        println!("cargo::rustc-cdylib-link-arg=-mmacosx-version-min=12.0");
    }
}
