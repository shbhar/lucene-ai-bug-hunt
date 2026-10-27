# On main, CheckIndex's HNSW graph test reports valid segments written by Lucene 9.12.0-9.12.1 and 10.0.0-10.1.0 as broken ('has repeated neighbors'), because the check has no version gate

Bug LFV-543 · Area: index / CheckIndex HNSW graph check · Priority: low · Impact: wrong error · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You open, on Lucene main, an index that Lucene 9.12.0, 9.12.1, 10.0.0 or 10.1.0 wrote with the default codec, and run CheckIndex at level 2 or 3. You expect it to pass: main opens, reads, searches and merges the index correctly, and upstream calls repeated HNSW neighbours benign. Instead, when an old unmerged segment's graph lists the same neighbour twice (those releases' connectComponents step could add an existing link again, and their writers did not de-duplicate), CheckIndex reports 'has repeated neighbors': with failFast it throws CheckIndexException ('Segment... check failed'), otherwise it prints 'test: hnsw graphs.........ERROR', because the rule applies to every segment whatever version wrote it.

## Expected vs actual

- Expected: You expect it to pass: main opens, reads, searches and merges the index correctly, and upstream calls repeated HNSW neighbours benign.
- Actual: When an old unmerged segment's graph lists the same neighbour twice (those releases' connectComponents step could add an existing link again, and their writers did not de-duplicate), CheckIndex reports 'has repeated neighbors': with failFast it throws CheckIndexException ('Segment... check failed'), otherwise it prints 'test: hnsw graphs.........ERROR', because the rule applies to every segment whatever version wrote it.

## Files

- [src/org/apache/lucene/index/TestCheckIndexOldGraphRepeatedNeighbors.java](src/org/apache/lucene/index/TestCheckIndexOldGraphRepeatedNeighbors.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/backward-codecs/build/libs/lucene-backward-codecs-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestCheckIndexOldGraphRepeatedNeighbors
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
