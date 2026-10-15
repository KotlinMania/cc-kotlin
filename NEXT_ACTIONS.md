# Immediate Actions - High-Value Files

Based on AST analysis, here are the concrete next steps.

## Summary

- **Files Present:** 8/12 (66.7%)
- **Function parity:** 85/248 matched (target 132) — 34.3%
- **Class/type parity:** 15/33 matched (target 32) — 45.5%
- **Combined symbol parity:** 100/281 matched (target 164) — 35.6%
- **Average inline-code cosine:** 0.10 (function body across 6 matched files)
- **Average documentation cosine:** 0.29 (doc text across 6 matched files)
- **Cheat-zeroed Files:** 2
- **Critical Issues:** 8 files with <0.60 function similarity
- **Needs Review:** 0 files with 0.60-0.84 function similarity
- **Excellent:** 0 files with >=0.85 function similarity

## Priority 1: Fix Incomplete High-Dependency Files

No incomplete high-dependency files detected.

## Priority 2: Port Missing High-Value Files

Critical missing files (>10 dependencies):

No missing high-value files detected.

## Detailed Work Items

Every matched file is listed below with function and type symbol parity.

### 1. lib

- **Target:** `cc.Error [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 1365010.0
- **Functions:** 12/140 matched (target 21)
- **Missing functions:** `find`, `find_vs_version`, `find_tool`, `from`, `fmt`, `new`, `new`, `include`, `includes`, `define`, `object`, `objects`, `flag`, `flags`, `remove_flag`, `ar_flag`, `asm_flag`, `flag_if_supported`, `try_flags_from_environment`, `shared_flag`, `static_flag`, `no_default_flags`, `file`, `files`, `get_files`, `cpp`, `cuda`, `cudart`, `ccbin`, `std`, `warnings_into_errors`, `warnings`, `extra_warnings`, `cpp_link_stdlib`, `cpp_link_stdlib_static`, `cpp_set_stdlib`, `target`, `host`, `opt_level`, `opt_level_str`, `debug`, `debug_str`, `force_frame_pointer`, `out_dir`, `compiler`, `archiver`, `ranlib`, `cargo_metadata`, `cargo_warnings`, `cargo_debug`, `cargo_output`, `link_lib_modifier`, `pic`, `use_plt`, `emit_rerun_if_env_changed`, `static_crt`, `shell_escaped_flags`, `inherit_rustflags`, `prefer_clang_cl_over_msvc`, `__set_env`, `is_flag_supported`, `ensure_check_file`, `is_flag_supported_inner`, `try_compile`, `compile`, `compile_intermediates`, `try_compile_intermediates`, `compile_objects`, `create_compile_object_cmd`, `try_expand`, `expand`, `get_compiler`, `try_get_compiler`, `add_default_flags`, `add_inherited_rustflags`, `msvc_macro_assembler`, `assemble`, `assemble_progressive`, `apple_flags`, `cmd`, `prefer_clang`, `get_base_compiler`, `rustc_wrapper_fallback`, `env_tool`, `get_cpp_link_stdlib`, `get_archiver`, `try_get_archiver`, `try_get_archiver_and_flags`, `get_base_archiver`, `get_ranlib`, `try_get_ranlib`, `get_base_ranlib`, `get_base_archiver_variant`, `prefix_for_target`, `find_working_gnu_prefix`, `get_target`, `get_raw_target`, `get_is_cross_compile`, `get_opt_level`, `get_debug`, `get_debug_str`, `get_shell_escaped_flags`, `get_dwarf_version`, `get_force_frame_pointer`, `get_out_dir`, `getenv`, `provided_by_cargo`, `getenv_boolean`, `getenv_unwrap`, `getenv_unwrap_str`, `target_envs`, `getenv_with_target_prefixes`, `envflags`, `fix_env_for_apple_os`, `apple_sdk_root_inner`, `apple_sdk_root`, `apple_deployment_target`, `wasm_musl_sysroot`, `wasi_sysroot`, `cuda_file_count`, `which`, `search_programs`, `find_msvc_tools_find`, `find_msvc_tools_find_tool`, `get_env`, `autodetect_wasi_compiler`, `default`, `compute_is_disabled`
- **Types:** 3/10 matched (target 4)
- **Missing types:** `VsVers`, `CompilerFlag`, `Env`, `BuildCache`, `Build`, `Object`, `BuildEnvGetter`
- **Tests:** 1/1 matched

### 2. tool

- **Target:** `cc.Tool`
- **Similarity:** 0.19
- **Dependents:** 1
- **Priority Score:** 1013508.1
- **Functions:** 31/32 matched (target 34)
- **Missing functions:** `is_xctoolchain_clang`
- **Types:** 3/3 matched (target 8)
- **Missing types:** _none_

### 3. command_helpers

- **Target:** `cc.CommandHelpers`
- **Similarity:** 0.23
- **Dependents:** 0
- **Priority Score:** 32507.7
- **Functions:** 18/20 matched (target 48)
- **Missing functions:** `forward_all`, `drop`
- **Types:** 4/5 matched (target 10)
- **Missing types:** `ResetStderr`

### 4. parallel.job_token

- **Target:** `parallel.JobToken`
- **Similarity:** 0.03
- **Dependents:** 0
- **Priority Score:** 32009.7
- **Functions:** 14/14 matched (target 18)
- **Missing functions:** _none_
- **Types:** 3/6 matched (target 8)
- **Missing types:** `JobServer`, `HelperThread`, `ActiveJobServer`

### 5. parallel.async_executor

- **Target:** `parallel.AsyncExecutor`
- **Similarity:** 0.03
- **Dependents:** 0
- **Priority Score:** 10409.7
- **Functions:** 2/2 matched
- **Missing functions:** _none_
- **Types:** 1/2 matched (target 1)
- **Missing types:** `Output`

### 6. parallel.command_runner

- **Target:** `parallel.CommandRunner`
- **Similarity:** 0.09
- **Dependents:** 0
- **Priority Score:** 509.1
- **Functions:** 4/4 matched (target 5)
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 7. parallel.stderr

- **Target:** `parallel.Stderr`
- **Similarity:** 0.03
- **Dependents:** 0
- **Priority Score:** 409.7
- **Functions:** 4/4 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched
- **Missing types:** _none_

### 8. parallel.mod

- **Target:** `parallel.Mod [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 10.0
- **Functions:** 0/0 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched
- **Missing types:** _none_

## Success Criteria

For each file to be considered "complete":
- **Similarity ≥ 0.85** (Excellent threshold)
- All public APIs ported
- All tests ported
- Documentation ported
- port-lint header present

