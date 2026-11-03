# A persistent Monitor query index written before 10.3.0 keeps missing matches after an upgrade to 10.3.0 or later, until every query is registered again (the #14886 fix covers only indexes written from 10.3.0 on)

Finding LFV-1115 · Area: monitor / persistent query index (WritableQueryIndex, ReadonlyQueryIndex, QueryDecomposer) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, reproduced on Lucene main but not yet independently re-verified, and not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You register the query f:alpha OR f:beta in a persistent Monitor on 10.2.2, upgrade to 10.3.0 or later, open the same query index and match a document whose field f is 'alpha'; you expect the query to match, as it does on a freshly registered Monitor. It does not, with no error: the stored branch documents keep the 10.2.2 writer's hash order of the branches, which follows that JVM's hash seed (in the repro's index, cache ids q_0 = beta, q_1 = alpha), while 10.3.0 and later rebuild the cache in clause order (q_0 = alpha, q_1 = beta), so the presearcher selects branch q_1 and the Monitor runs f:beta on the document.

## Expected vs actual

- Expected: You expect the query to match, as it does on a freshly registered Monitor.
- Actual: While the reported behaviour is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/monitor/TestMonitorIndexFromOlderRelease.java](src/org/apache/lucene/monitor/TestMonitorIndexFromOlderRelease.java) (JUnit test)
- [fixture-src/WriteMonitorIndex.java](fixture-src/WriteMonitorIndex.java) (writes the fixture the test reads, as Run it says; not compiled with the test)

## Run it

- Tested on Lucene main e1faeba08bde (2026-11-02): reproduced.
- Fixture (required): the test reads monitor-index-10.2.2.zip, a persistent Monitor query index with one registered query, written by Lucene 10.2.2, from its package on the class path. This site ships no binary index files, so the recipe first makes it with fixture-src/WriteMonitorIndex.java on the 10.2.2 release jars (Maven Central) and puts it in src/, which the run puts on the class path. The writer JVM's tests.seed fixes the order in which 10.2.2 stores the query's two disjuncts; CAFEBABE stores them reversed, the case that loses matches.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/backward-codecs/build/libs/lucene-backward-codecs-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/monitor/build/libs/lucene-monitor-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

# required fixture monitor-index-10.2.2.zip, written by the Lucene 10.2.2 release: the lucene-core, lucene-monitor, lucene-memory and lucene-analysis-common 10.2.2 jars (Maven Central)
FCP=lucene-core-10.2.2.jar:lucene-monitor-10.2.2.jar:lucene-memory-10.2.2.jar:lucene-analysis-common-10.2.2.jar
javac -d fixture-classes -cp "$FCP" fixture-src/WriteMonitorIndex.java
java -Dtests.seed=CAFEBABE -cp "fixture-classes:$FCP" WriteMonitorIndex fixture-index
mkdir -p src/org/apache/lucene/monitor && (cd fixture-index && rm -f write.lock && zip -q -X ../src/org/apache/lucene/monitor/monitor-index-10.2.2.zip *)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.monitor.TestMonitorIndexFromOlderRelease
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

1 other file(s) of the recorded repro are not exported.

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
