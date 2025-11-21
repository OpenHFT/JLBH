# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

JLBH (Java Latency Benchmark Harness) is a specialised benchmarking library for measuring end-to-end latency in Java applications under realistic throughput conditions. Unlike JMH micro-benchmarks, JLBH is designed to benchmark code "in context" and account for coordinated omission. It is particularly suited for producer/consumer workloads where iteration start and completion may occur on different threads.

## Build & Test Commands

```bash
# Verify build and run all tests
mvn -q verify

# Clean build from scratch
mvn -q clean verify

# Run a specific test class
mvn -q test -Dtest=JLBHTest

# Run a single test method
mvn -q test -Dtest=JLBHTest#shouldProvideResultData

# Compile only (skip tests)
mvn -q compile

# Skip tests during verify
mvn -q verify -DskipTests
```

## Language & Character-Set Requirements

**Critical**: All code and documentation must use:
- **British English** spelling (`organisation`, `licence`) except for Java keywords like `synchronized`
- **ISO-8859-1** character encoding only (code points 0-255)
- **No Unicode characters**: Use ASCII equivalents like `>=` instead of special symbols
- Verify with: `iconv -f ascii -t ascii <file>`

See AGENTS.md for complete language policy and rationale.

## Architecture Overview

### Core Components

**JLBH** (src/main/java/net/openhft/chronicle/jlbh/JLBH.java)
- The main orchestrator that manages the entire benchmark lifecycle
- Runs on a single thread (annotated `@SingleThreaded`)
- Provides `sample(long durationNs)` to record end-to-end latencies
- Provides `addProbe(String)` to create additional `NanoSampler` instances for measuring sub-stages
- Can run standalone via `start()` or integrate with Chronicle EventLoop via `eventLoopHandler(EventLoop)`

**JLBHOptions** (src/main/java/net/openhft/chronicle/jlbh/JLBHOptions.java)
- Builder-style configuration object defining all benchmark parameters
- Key settings: `throughput()`, `iterations()`, `runs()`, `warmUpIterations()`, `accountForCoordinatedOmission()`, `recordOSJitter()`
- Default throughput: 10,000 ops/sec; default runs: 3

**JLBHTask** (src/main/java/net/openhft/chronicle/jlbh/JLBHTask.java)
- User-implemented interface defining the workload to benchmark
- Lifecycle methods called by JLBH:
  - `init(JLBH)`: Setup phase, register probes
  - `run(long startTimeNs)`: Execute benchmark iteration (called for each iteration)
  - `warmedUp()`: Notification after warmup completes
  - `runComplete()`: Notification after each run
  - `complete()`: Final cleanup

**Histograms & Results**
- Each probe (end-to-end, custom probes, OS jitter) records samples into a `Histogram` from chronicle-core
- Histograms use logarithmic bucketing with 35-bit range and 8 significant figures
- Immutable result objects: `JLBHResult`, `ProbeResult`, `RunResult`
- Use `ThreadSafeJLBHResultConsumer` to retrieve results from another thread

### Threading Model

- **Single-threaded harness**: All JLBHTask lifecycle methods run on the single JLBH thread
- **User code may spawn threads**: The benchmarked code within `JLBHTask.run()` can be multi-threaded
- **Sample recording thread-safety**: Only the JLBH harness thread should call `jlbh.sample()` or probe samplers
- **Event loop integration**: Via `eventLoopHandler()` instead of `start()` (requires coordinated omission accounting enabled)

### Execution Flow

1. **Initialization**: User configures `JLBHOptions`, creates `JLBH` instance, calls `JLBHTask.init()`
2. **Warm-up**: Runs `warmUpIterations` times to allow JIT compilation (default ~12k iterations)
3. **Measurement runs**: Executes `runs` times (default 3), each with `iterations` samples
4. **Completion**: Prints summary, constructs immutable `JLBHResult`, calls `JLBHTask.complete()`

See src/main/adoc/benchmark-lifecycle.adoc for visual flow diagram.

### Coordinated Omission

JLBH accounts for coordinated omission by default (`accountForCoordinatedOmission = true`):
- `startTimeNs` passed to `run()` is the *calculated ideal start time*, not `System.nanoTime()`
- Harness busy-waits if current time is before scheduled start time
- Disabling this feature means start time is simply based on throughput without waiting

### OS Jitter Monitoring

Enabled by default via `recordOSJitter(true)`:
- Background thread `OSJitterMonitor` repeatedly samples `System.nanoTime()` to detect scheduler delays
- Records jitter > `recordJitterGreaterThanNs` (default: 1000ns) into separate histogram
- Incurs overhead; disable with `recordOSJitter(false)` for minimal-overhead benchmarks

## Key Design Patterns

**Probes for Multi-Stage Benchmarks**
```java
class MyTask implements JLBHTask {
    private JLBH jlbh;
    private NanoSampler stage1Probe;

    public void init(JLBH jlbh) {
        this.jlbh = jlbh;
        this.stage1Probe = jlbh.addProbe("stage1");
    }

    public void run(long startTimeNs) {
        long stage1Start = System.nanoTime();
        // ... stage 1 work ...
        stage1Probe.sampleNanos(System.nanoTime() - stage1Start);

        // ... remaining work ...
        jlbh.sample(System.nanoTime() - startTimeNs);
    }
}
```

**Typical Benchmark Setup**
```java
JLBHOptions options = new JLBHOptions()
    .warmUpIterations(100_000)
    .iterations(1_000_000)
    .throughput(50_000)
    .runs(3)
    .jlbhTask(myTask);
new JLBH(options).start();
```

**Thread-Safe Result Retrieval**
```java
JLBHResultConsumer resultConsumer = JLBHResultConsumer.newThreadSafeInstance();
JLBH jlbh = new JLBH(options, System.out, resultConsumer);
// Run on separate thread
new Thread(() -> jlbh.start()).start();
// Retrieve results from main thread after completion
JLBHResult result = resultConsumer.get();
```

## Documentation Structure

All requirements and design decisions are in src/main/adoc/:
- **project-requirements.adoc**: Formal requirements specification with tagged requirements (FN-xxx, NF-P-xxx, etc.)
- **decision-log.adoc**: Architectural decision records (ADRs) following Nine-Box taxonomy
- **architecture.adoc**: High-level architecture, components, execution flow, threading model
- **benchmark-lifecycle.adoc**: Visual diagram of benchmark execution phases
- **jlbh-cookbook.adoc**: Common usage patterns and recipes
- **results-interpretation-guide.adoc**: How to interpret percentile output

When making changes:
1. Update relevant .adoc files first (if changing requirements/design)
2. Update code and tests
3. Verify all three stay synchronised in same commit

## Javadoc Standards

Follow guidelines from AGENTS.md:
- Document *behavioural contracts*, edge cases, thread-safety, units, performance characteristics
- Do NOT restate the obvious ("Gets the value")
- First sentence must be concise (becomes summary line)
- Remove autogenerated Javadoc for trivial getters/setters
- Explain *why* and *how*, not just *what*

## Test Examples

Test files in src/test/java/net/openhft/chronicle/jlbh/ demonstrate usage:
- `ExampleJLBHMain`: Basic command-line harness demonstration
- `SimpleBenchmark`: Minimal benchmark example
- `SimpleOSJitterBenchmark`: Demonstrates OS jitter recording and custom probes
- `JLBHTest`: Unit test showing programmatic result extraction
- `JLBHIntegrationTest`: Example integration test

## Performance Requirements

From project-requirements.adoc (NF-P requirements):
- Overhead per sample: < 100 ns when no additional probes active
- Histogram generation: Support >= 200M iterations without heap pressure
- Warm-up rule of thumb: ~30% of run iterations (default uses JIT compile threshold * 6/5)

## Dependencies

Key dependencies from pom.xml:
- **chronicle-core**: Provides `Histogram`, `NanoSampler`, threading utilities
- **affinity**: CPU affinity control via `AffinityLock`
- **chronicle-threads**: Event loop integration (test scope)

## Common Gotchas

- JLBH is `@SingleThreaded` - the harness itself runs on one thread
- The `startTimeNs` parameter to `run()` is NOT `System.nanoTime()` - it's the calculated ideal start time
- Warm-up iterations use ~12k by default; adjust with `warmUpIterations()` if benchmark takes long to stabilise
- OS jitter monitoring is enabled by default and adds overhead; explicitly disable if needed
- When using `eventLoopHandler()`, coordinated omission must remain enabled
- CSV export: Use `JLBHResultSerializer.runResultToCSV(jlbhResult)` (writes to `result.csv` by default)

## Non-Functional Quality Attributes

From README.adoc summary:
- **Performance**: < 100 ns overhead per sample, >= 200M iterations supported
- **Reliability**: Graceful abort on interruption/timeout, immutable thread-safe results
- **Usability**: Fluent API, runnable in <= 10 LOC, human-readable ASCII output
- **Portability**: Pure Java, runtime-detected JDK optimisations
- **Maintainability**: >= 80% test coverage (current: ~83% line, ~78% branch per pom.xml)

## Commit & PR Guidelines

From AGENTS.md:
- Subject line <= 72 chars, imperative mood
- Body: root cause -> fix -> measurable impact
- Run `mvn -q verify` before opening PR
- Keep PRs focused; avoid bundling unrelated changes
- Re-run build after addressing review comments
