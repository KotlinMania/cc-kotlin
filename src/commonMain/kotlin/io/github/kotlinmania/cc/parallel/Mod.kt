// port-lint: source src/parallel/mod.rs
package io.github.kotlinmania.cc.parallel

// mod async_executor;
// mod command_runner;
// mod job_token;
// pub(crate) mod stderr;
//
// Each of the modules above becomes a sibling Kotlin file in this package.

// pub(crate) use command_runner::run_commands_in_parallel;
//
// Per the workspace `mod.rs` re-export workflow, this file does not introduce a central alias
// for the re-export. Callers should reference the original symbol directly (the Kotlin port of
// `src/parallel/command_runner.rs`) and use Kotlin import aliasing if they need to preserve the
// identifier `run_commands_in_parallel`.
//
// Callers migrated:
// - none
//
// Projected callers (Rust):
// - `libwebrtc-kotlin` imports `cc` (see `/Volumes/stuff/Projects/kotlinmania/cc-kotlin/RUST_CALLERS.md`).
