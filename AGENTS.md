# Chronicle JLBH AGENTS

- Follow repository `AGENTS.md` as the base rules; this file adds JLBH specifics. Durable docs live in `src/main/docs/` with the landing page at `README.adoc`.
- Module purpose: Java Latency Benchmark Harness for running context-aware latency/throughput benchmarks with Chronicle-friendly patterns.
- Build commands: full build `mvn -q clean verify`; module-only without tests `mvn -pl JLBH -am -DskipTests install`.
- Quality gates: keep Checkstyle/SpotBugs clean; ensure benchmarks remain deterministic and reproducible; guard against benchmark harness changes that alter measurement accuracy.
- Documentation: maintain Nine-Box IDs in `src/main/docs/project-requirements.adoc` and link decisions/tests/benchmarks to them; British English, ASCII/ISO-8859-1, `:source-highlighter: rouge`.
- Guardrails: avoid introducing benchmark code that masks coordinated omission; document any new CLI options or environment knobs in the docs.
