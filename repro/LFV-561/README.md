# With failFast off (the default), CheckIndex reports an index clean ('No problems were detected', Status.clean true, exit code 0) although its HNSW graph test printed ERROR, because testSegment never rethrows hnswGraphsStatus.error

Bug LFV-561 · Area: index / CheckIndex HNSW graph check · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run CheckIndex at level 2 or 3 (the command line with -level 2, or setLevel) without failFast on an index whose HNSW graph is broken, for example a neighbour that is not on its level or a neighbour list out of order. You expect the segment to count as bad, as it does for every other sub-test: Status.clean false, exit code 1, and -exorcise able to act; with failFast on, the same index does throw CheckIndexException. Instead the HNSW sub-test prints 'test: hnsw graphs.........ERROR:...' and only stores the exception, because testSegment's rethrow chain has no clause for hnswGraphsStatus.error, so the run ends with 'No problems were detected with this index.', Status.clean true, numBadSegments 0 and exit code 0.

The test shows that CheckIndex at level 2 without failFast reports an index clean although its HNSW graph test failed (one byte of.vex edited so node 0's level-0 neighbours 1,2,3 become 1,1,2; checksum footer rewritten).

## Expected vs actual

Expected: Status.clean == false once 'test: hnsw graphs' prints ERROR; actual: 'ERROR:... repeated neighbors of node 0 with value 1', then 'No problems were detected with this index.' and clean == true (-ea and -da).

## Files

- [src/org/apache/lucene/index/TestCheckIndexHnswGraphError.java](src/org/apache/lucene/index/TestCheckIndexHnswGraphError.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed 5EED0561
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=5EED0561 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestCheckIndexHnswGraphError
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
