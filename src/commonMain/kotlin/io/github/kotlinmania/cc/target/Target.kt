// port-lint: source src/target.rs
package io.github.kotlinmania.cc.target

// Parsing of `rustc` target names to match the values exposed to Cargo
// build scripts (`CARGO_CFG_*`).

// mod apple;
// mod generated;
// mod llvm;
// mod parser;
//
// Each of the modules above becomes a sibling Kotlin file in this package.

// pub(crate) use parser::TargetInfoParser;
//
// Per the workspace `mod.rs` re-export workflow, this file does not introduce a central alias
// for the re-export. Callers should reference the original symbol directly (the Kotlin port of
// `src/target/parser.rs`) and use Kotlin import aliasing if they need to preserve the identifier
// `TargetInfoParser`.
//
// Callers migrated:
// - none

/**
 * Information specific to a `rustc` target.
 *
 * See <https://doc.rust-lang.org/cargo/appendix/glossary.html#target>.
 */
data class TargetInfo(
    /**
     * The full architecture, including the subarchitecture.
     *
     * This differs from the value of the `target_arch` configuration predicate, which only
     * specifies the overall architecture, which is too coarse for certain cases.
     */
    val fullArch: String,
    /**
     * The overall target architecture.
     *
     * This is the same as the value of the `target_arch` configuration predicate.
     */
    val arch: String,
    /**
     * The target vendor.
     *
     * This is the same as the value of the `target_vendor` configuration predicate.
     */
    val vendor: String,
    /**
     * The operating system, or `none` on bare-metal targets.
     *
     * This is the same as the value of the `target_os` configuration predicate.
     */
    val os: String,
    /**
     * The environment on top of the operating system.
     *
     * This is the same as the value of the `target_env` configuration predicate.
     */
    val env: String,
    /**
     * The ABI on top of the operating system.
     *
     * This is the same as the value of the `target_abi` configuration predicate.
     */
    val abi: String,
)
