# cc-kotlin in Kotlin

[![GitHub link](https://img.shields.io/badge/GitHub-KotlinMania%2Fcc--kotlin-blue.svg)](https://github.com/KotlinMania/cc-kotlin)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.kotlinmania/cc-kotlin)](https://central.sonatype.com/artifact/io.github.kotlinmania/cc-kotlin)
[![Build status](https://img.shields.io/github/actions/workflow/status/KotlinMania/cc-kotlin/ci.yml?branch=main)](https://github.com/KotlinMania/cc-kotlin/actions)

This is a Kotlin Multiplatform line-by-line transliteration port of [`rust-lang/cc-rs`](https://github.com/rust-lang/cc-rs).

**Original Project:** This port is based on [`rust-lang/cc-rs`](https://github.com/rust-lang/cc-rs). All design credit and project intent belong to the upstream authors; this repository is a faithful port to Kotlin Multiplatform with no behavioural changes intended.

### Porting status

This is an **in-progress port**. The goal is feature parity with the upstream Rust crate while providing a native Kotlin Multiplatform API. Every Kotlin file carries a `// port-lint: source <path>` header naming its upstream Rust counterpart so the AST-distance tool can track provenance.

---

## Upstream README — `rust-lang/cc-rs`

> The text below is reproduced and lightly edited from [`https://github.com/rust-lang/cc-rs`](https://github.com/rust-lang/cc-rs). It is the upstream project's own description and remains under the upstream authors' authorship; links have been rewritten to absolute upstream URLs so they continue to resolve from this repository.

## cc-rs

A library for
[Cargo build scripts](https://doc.rust-lang.org/cargo/reference/build-scripts.html)
to compile a set of C/C++/assembly/CUDA files into a static archive for Cargo to
link into the crate being built. This crate does not compile code itself; it
calls out to the default compiler for the platform. This crate will
automatically detect situations such as cross compilation and various
environment variables and will build code appropriately.

Refer to the [documentation](https://docs.rs/cc) for detailed usage
instructions.

## License

This project is licensed under either of

 * Apache License, Version 2.0, ([LICENSE-APACHE](https://github.com/rust-lang/cc-rs/blob/HEAD/LICENSE-APACHE) or
   https://www.apache.org/licenses/LICENSE-2.0)
 * MIT license ([LICENSE-MIT](https://github.com/rust-lang/cc-rs/blob/HEAD/LICENSE-MIT) or
   https://opensource.org/license/mit)

at your option.

### Contribution

Unless you explicitly state otherwise, any contribution intentionally submitted
for inclusion in cc-rs by you, as defined in the Apache-2.0 license, shall be
dual licensed as above, without any additional terms or conditions.

---

## About this Kotlin port

### Installation

```kotlin
dependencies {
    implementation("io.github.kotlinmania:cc-kotlin:0.1.0-SNAPSHOT")
}
```

### Building

```bash
./gradlew build
./gradlew test
```

### Targets

- macOS arm64
- Linux x64
- Windows mingw-x64
- iOS arm64 / simulator-arm64 (Swift export + XCFramework)
- JS (browser + Node.js)
- Wasm-JS (browser + Node.js)
- Android (API 24+)

### Porting guidelines

See [AGENTS.md](AGENTS.md) and [CLAUDE.md](CLAUDE.md) for translator discipline, port-lint header convention, and Rust → Kotlin idiom mapping.

### License

This Kotlin port is distributed under the same MIT license as the upstream [`rust-lang/cc-rs`](https://github.com/rust-lang/cc-rs). See [LICENSE](LICENSE) (and any sibling `LICENSE-*` / `NOTICE` files mirrored from upstream) for the full text.

Original work copyrighted by the cc-rs authors.  
Kotlin port: Copyright (c) 2026 Sydney Renee and The Solace Project.

### Acknowledgments

Thanks to the [`rust-lang/cc-rs`](https://github.com/rust-lang/cc-rs) maintainers and contributors for the original Rust implementation. This port reproduces their work in Kotlin Multiplatform; bug reports about upstream design or behavior should go to the upstream repository.
