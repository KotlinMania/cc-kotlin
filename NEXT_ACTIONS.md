# Immediate Actions - High-Value Files

Based on AST analysis, here are the concrete next steps.

## Summary

- **Files Present:** 11/12 (91.7%)
- **Function parity:** 229/248 matched (target 294) — 92.3%
- **Class/type parity:** 23/33 matched (target 47) — 69.7%
- **Combined symbol parity:** 252/281 matched (target 341) — 89.7%
- **Average inline-code cosine:** 0.11 (function body across 9 matched files)
- **Average documentation cosine:** 0.20 (doc text across 9 matched files)
- **Cheat-zeroed Files:** 2
- **Critical Issues:** 11 files with <0.60 function similarity
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

### 2. lib

- **Target:** `cc.Build [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 145010.0
- **Functions:** 131/140 matched (target 147)
- **Missing functions:** `find`, `find_vs_version`, `find_tool`, `from`, `fmt`, `new`, `object`, `get_env`, `compute_is_disabled`
- **Types:** 6/10 matched
- **Missing types:** `VsVers`, `Env`, `Object`, `BuildEnvGetter`
- **Tests:** 1/1 matched

### 3. utilities

- **Target:** `cc.Utilities`
- **Similarity:** 0.04
- **Dependents:** 0
- **Priority Score:** 41309.6
- **Functions:** 6/10 matched (target 13)
- **Missing functions:** `fmt`, `fmt`, `fmt`, `drop`
- **Types:** 3/3 matched (target 4)
- **Missing types:** _none_

### 4. command_helpers

- **Target:** `cc.CommandHelpers`
- **Similarity:** 0.23
- **Dependents:** 0
- **Priority Score:** 32507.7
- **Functions:** 18/20 matched (target 49)
- **Missing functions:** `forward_all`, `drop`
- **Types:** 4/5 matched (target 10)
- **Missing types:** `ResetStderr`

### 5. parallel.job_token

- **Target:** `parallel.JobToken`
- **Similarity:** 0.03
- **Dependents:** 0
- **Priority Score:** 32009.7
- **Functions:** 14/14 matched (target 18)
- **Missing functions:** _none_
- **Types:** 3/6 matched (target 8)
- **Missing types:** `JobServer`, `HelperThread`, `ActiveJobServer`

### 6. flags

- **Target:** `cc.Flags`
- **Similarity:** 0.31
- **Dependents:** 0
- **Priority Score:** 21606.9
- **Functions:** 13/15 matched
- **Missing functions:** `flag_not_empty_generic`, `check`
- **Types:** 1/1 matched (target 2)
- **Missing types:** _none_
- **Tests:** 6/7 matched

### 7. tempfile

- **Target:** `cc.Tempfile`
- **Similarity:** 0.08
- **Dependents:** 0
- **Priority Score:** 10809.2
- **Functions:** 6/7 matched
- **Missing functions:** `create_named`
- **Types:** 1/1 matched (target 3)
- **Missing types:** _none_

### 8. parallel.async_executor

- **Target:** `parallel.AsyncExecutor`
- **Similarity:** 0.03
- **Dependents:** 0
- **Priority Score:** 10409.7
- **Functions:** 2/2 matched
- **Missing functions:** _none_
- **Types:** 1/2 matched (target 1)
- **Missing types:** `Output`

### 9. parallel.command_runner

- **Target:** `parallel.CommandRunner`
- **Similarity:** 0.09
- **Dependents:** 0
- **Priority Score:** 509.1
- **Functions:** 4/4 matched (target 5)
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 10. parallel.stderr

- **Target:** `parallel.Stderr`
- **Similarity:** 0.03
- **Dependents:** 0
- **Priority Score:** 409.7
- **Functions:** 4/4 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched
- **Missing types:** _none_

### 11. parallel.mod

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

