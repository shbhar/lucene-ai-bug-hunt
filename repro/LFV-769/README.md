# Monitor.match can throw NullPointerException while queries are being registered (or while a read-only Monitor refreshes), because QueryIndex keeps its per-reader term filters in a plain HashMap that the searcher refresh and reader-close listeners modify while match threads read it without a lock

Bug LFV-769 · Area: monitor / QueryIndex term-filter map (searcher refresh vs concurrent match, writable and read-only Monitor) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You share one Monitor between threads: some threads call Monitor.match on documents while another registers new queries, and each register commits and opens a new query-index searcher, which the Monitor's design for searches during commits allows. You expect every match of a valid document to return its matching queries. Instead, when the internal map from searcher to term filter (a plain java.util.HashMap) grows, a concurrent match can look up its own searcher's entry and get null, and Monitor.match throws NullPointerException from TermFilteredPresearcher.buildQuery; the read-only Monitor fails the same way while its refresh runs.

## Expected vs actual

- Expected: You expect every match of a valid document to return its matching queries.
- Actual: When the internal map from searcher to term filter (a plain java.util.HashMap) grows, a concurrent match can look up its own searcher's entry and get null, and Monitor.match throws NullPointerException from TermFilteredPresearcher.buildQuery; the read-only Monitor fails the same way while its refresh runs.

## Files

- [src/org/apache/lucene/monitor/TestMonitorTermFilterMapRace.java](src/org/apache/lucene/monitor/TestMonitorTermFilterMapRace.java) (JUnit test)
- [src/org/apache/lucene/monitor/TestReadonlyMonitorTermFilterMapRace.java](src/org/apache/lucene/monitor/TestReadonlyMonitorTermFilterMapRace.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/monitor/build/libs/lucene-monitor-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.monitor.TestMonitorTermFilterMapRace

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.monitor.TestReadonlyMonitorTermFilterMapRace
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
