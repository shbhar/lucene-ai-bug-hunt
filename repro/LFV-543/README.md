# On main and branch_11x, CheckIndex's HNSW graph test (level 2 or 3) reports a 'has repeated neighbors' error for segments that Lucene 9.12.0-9.12.1 or 10.0.0-10.1.0 wrote with a repeated HNSW neighbour, because the check has no version gate

Finding LFV-543 · Area: index / CheckIndex HNSW graph check · Priority: low · Impact: wrong error · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You open, on Lucene main, an index that Lucene 9.12.0, 9.12.1, 10.0.0 or 10.1.0 wrote with the default codec, and run CheckIndex at level 2 or 3. You expect it to pass: main opens the index, reads every vector, answers kNN queries and merges it, and in #14127 a Lucene committer (msokolov) called repeated HNSW neighbours benign and proposed a version gate for this check. No javadoc says whether a neighbour list may repeat a neighbour (CheckIndex, HnswGraph and Lucene99HnswVectorsFormat were read), so the expectation rests on that proposal. Instead, when the graph of an old segment that has not been merged on main lists the same neighbour twice (those releases' connectComponents step could add an existing link again, and their writers did not de-duplicate), CheckIndex reports 'has repeated neighbors': with failFast (an API setting; the command line has no option for it) it throws CheckIndexException ('Segment ... check failed'); otherwise it prints 'test: hnsw graphs.........ERROR' and still ends with 'No problems were detected with this index.' (Status.clean true, exit code 0). The rule applies to every segment whatever version wrote it.

## Expected vs actual

- Expected: You expect it to pass: main opens the index, reads every vector, answers kNN queries and merges it, and in #14127 a Lucene committer (msokolov) called repeated HNSW neighbours benign and proposed a version gate for this check.
- Actual: When the graph of an old segment that has not been merged on main lists the same neighbour twice (those releases' connectComponents step could add an existing link again, and their writers did not de-duplicate), CheckIndex reports 'has repeated neighbors': with failFast (an API setting; the command line has no option for it) it throws CheckIndexException ('Segment ... check failed'); otherwise it prints 'test: hnsw graphs.........ERROR' and still ends with 'No problems were detected with this index.' (Status.clean true, exit code 0).

## Files

- [src/org/apache/lucene/index/TestCheckIndexOldGraphRepeatedNeighbors.java](src/org/apache/lucene/index/TestCheckIndexOldGraphRepeatedNeighbors.java) (JUnit test)
- [fixture-src/DupGen3.java](fixture-src/DupGen3.java) (writes the fixture the test reads, as Run it says; not compiled with the test)

## Run it

- Tested on Lucene main 1ae70c6e45b8 (a commit of 2026-11-19): still reproduces.
- Tested on Lucene main ea57360912ed (a commit of 2026-10-24): reproduced.
- Fixtures: the test reads repeated-neighbors.9.12.0.zip, repeated-neighbors.10.0.0.zip and repeated-neighbors.10.1.0.zip, indexes of 1,000 one-dimensional vectors written by those releases whose HNSW graphs list a neighbour twice, from its package on the class path. This site ships no binary index files, so the recipe first makes them with fixture-src/DupGen3.java and each release's lucene-core jar (Maven Central): it tries seeds 0 to 99 and keeps the first index with a repeated neighbour, and the zips go to src/, which the run puts on the class path; without them every test fails for another reason. Gradle alternative: not run for this finding. The test also needs the three fixture zips beside it and the backward-codecs module on the test class path, so use the recipe below.
- How to read the outcome: a test that fails with 'CheckIndex rejects a valid index written by Lucene &lt;version&gt;' is the finding reproducing; a failure with 'fixture for &lt;version&gt;' or 'fixture should contain a graph with a repeated neighbour' means the fixture step did not work, and 'Format version is not supported' means the backward-codecs jar is not on the class path.
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/backward-codecs/build/libs/lucene-backward-codecs-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

# fixtures repeated-neighbors.<version>.zip, written by the 9.12.0, 10.0.0 and 10.1.0 releases: lucene-core-<version>.jar
# of each (Maven Central)
for v in 9.12.0 10.0.0 10.1.0; do
  javac -d fixture-classes-$v -cp lucene-core-$v.jar fixture-src/DupGen3.java
  java -cp fixture-classes-$v:lucene-core-$v.jar DupGen3 fixture-$v 0 100 1 12 32 661 0.4185 18
  (cd fixture-$v/seed-* && zip -q -X ../../src/org/apache/lucene/index/repeated-neighbors.$v.zip *)
done

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestCheckIndexOldGraphRepeatedNeighbors
```

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
