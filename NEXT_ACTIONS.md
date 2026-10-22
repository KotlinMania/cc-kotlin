# Immediate Actions - High-Value Files

Based on AST analysis, here are the concrete next steps.

## Summary

- **Files Present:** 9/12 (75.0%)
- **Function parity:** 104/108 matched (target 155) — 96.3%
- **Class/type parity:** 22/23 matched (target 42) — 95.7%
- **Combined symbol parity:** 126/131 matched (target 197) — 96.2%
- **Average inline-code cosine:** 0.12 (function body across 9 matched files)
- **Average documentation cosine:** 0.20 (doc text across 9 matched files)
- **Cheat-zeroed Files:** 0
- **Critical Issues:** 9 files with <0.60 function similarity
- **Needs Review:** 0 files with 0.60-0.84 function similarity
- **Excellent:** 0 files with >=0.85 function similarity

## Priority 1: Fix Incomplete High-Dependency Files

No incomplete high-dependency files detected.

## Priority 2: Port Missing High-Value Files

Critical missing files (>10 dependencies):

No missing high-value files detected.

## Detailed Work Items

Every matched file is listed below with function and type symbol parity.

### 1. tool

- **Target:** `cc.Tool`
- **Similarity:** 0.19
- **Dependents:** 1
- **Priority Score:** 1013508.1
- **Functions:** 31/32 matched (target 34)
- **Missing functions:** `is_xctoolchain_clang`
- **Types:** 3/3 matched (target 8)
- **Missing types:** _none_

### 2. command_helpers

- **Target:** `cc.CommandHelpers`
- **Similarity:** 0.25
- **Dependents:** 0
- **Priority Score:** 12507.5
- **Functions:** 19/20 matched (target 51)
- **Missing functions:** `forward_all`
- **Types:** 5/5 matched (target 11)
- **Missing types:** _none_

### 3. flags

- **Target:** `cc.Flags`
- **Similarity:** 0.33
- **Dependents:** 0
- **Priority Score:** 11606.7
- **Functions:** 14/15 matched (target 17)
- **Missing functions:** `check`
- **Types:** 1/1 matched (target 2)
- **Missing types:** _none_
- **Tests:** 6/7 matched

### 4. tempfile

- **Target:** `cc.Tempfile`
- **Similarity:** 0.08
- **Dependents:** 0
- **Priority Score:** 10809.2
- **Functions:** 6/7 matched
- **Missing functions:** `create_named`
- **Types:** 1/1 matched (target 3)
- **Missing types:** _none_

### 5. parallel.job_token

- **Target:** `parallel.JobToken`
- **Similarity:** 0.03
- **Dependents:** 0
- **Priority Score:** 2009.7
- **Functions:** 14/14 matched (target 18)
- **Missing functions:** _none_
- **Types:** 6/6 matched (target 11)
- **Missing types:** _none_

### 6. utilities

- **Target:** `cc.Utilities`
- **Similarity:** 0.05
- **Dependents:** 0
- **Priority Score:** 1309.5
- **Functions:** 10/10 matched (target 17)
- **Missing functions:** _none_
- **Types:** 3/3 matched (target 4)
- **Missing types:** _none_

### 7. parallel.command_runner

- **Target:** `parallel.CommandRunner`
- **Similarity:** 0.09
- **Dependents:** 0
- **Priority Score:** 509.1
- **Functions:** 4/4 matched (target 5)
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 8. parallel.async_executor

- **Target:** `parallel.AsyncExecutor`
- **Similarity:** 0.03
- **Dependents:** 0
- **Priority Score:** 409.7
- **Functions:** 2/2 matched
- **Missing functions:** _none_
- **Types:** 2/2 matched
- **Missing types:** _none_

### 9. parallel.stderr

- **Target:** `parallel.Stderr`
- **Similarity:** 0.03
- **Dependents:** 0
- **Priority Score:** 409.7
- **Functions:** 4/4 matched
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

## Reexport / Wiring Modules

These files match `reexport_modules` patterns in `.ast_distance_config.json`. They are filtered out of
normal priority and missing-file ladders because they are wiring
modules, not direct logic ports. Consult them for call-site routing;
do not treat them as the next implementation target by default.

### Missing

| Source | Expected target | Deps | Source path | Expected path |
|--------|-----------------|------|-------------|---------------|
| `lib` | `Lib` | 0 | `lib.rs` | `Lib.kt` |
| `parallel.mod` | `parallel.Mod` | 0 | `parallel/mod.rs` | `parallel/Mod.kt` |

