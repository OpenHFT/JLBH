# AGENTS.md

## Scope
- JLBH is a latency benchmark harness; keep hot paths lean and avoid extra allocations.

## Build and test
- Preferred full check:
  - `mkdir -p logs`
  - `mvn verify -l logs/mvn-verify.log`
- Test example:
  - `mvn -Dtest=ClassName test -l logs/mvn-test.log`
- Review logs:
  - `rg -n '^\[(WARNING|ERROR)\]|SLF4J\(W\)|\bWARNING:|\bwarning:' logs/mvn-verify.log`
- Do not commit logs/.

## Constraints
- Java baseline: 8 (avoid newer language features).
- Source files must stay ISO-8859-1 (code points 0-255). Prefer ASCII; avoid smart quotes and non-breaking spaces.
- Preserve public APIs unless explicitly requested.
- Treat warnings as defects; keep logs clean.
- Avoid extra allocations or synchronisation on hot paths.

## Docs and review checklist
- Keep docs, tests, and code aligned; update AsciiDoc in `src/main/adoc` and `README` when behaviour changes.
- Javadoc must add contracts, edge cases, thread safety, units, or performance notes.
- For large mechanical changes, declare the transformation rule and keep it consistent.

## References
- `src/main/adoc/decision-log.adoc` and `src/main/adoc/project-requirements.adoc`.
- `OpenHFT/docs/Company-Wide-Tagging.adoc` for tagging and AsciiDoc conventions.
