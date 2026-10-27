# An unfrozen MemoryIndex does not reset its lazily built per-field state when more values are added to a field after a read, so later searches miss the new terms or points, throw ArrayIndexOutOfBoundsException, return unsorted SORTED_NUMERIC values or score with stale norms

Bug LFV-344 · Area: memory / MemoryIndex lazy per-field read state (sorted terms, points and doc-values preparation, norms) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You add a value to a field of a MemoryIndex that is not frozen, search it, add more values to the same field, and search again; you expect the second search to see everything, like a MemoryIndex built from the same adds, because freeze is the only documented point after which no data may be added and adding the same field several times is supported. Instead the field keeps the read state that the first read built lazily and nothing ever resets (the sorted term ids, the point min/max and 1D order, the sorted doc values, the cached norm): a term or IntPoint added later is silently not found (for example IntPoint p=2, search, p=1: newExactQuery(p, 1) no longer matches), SORTED_NUMERIC values come back as 2, 1, the score uses the old norm, or the search throws ArrayIndexOutOfBoundsException from the corrupted term dictionary (an AssertionError under -ea for some inputs).

## Expected vs actual

- Expected: You expect the second search to see everything, like a MemoryIndex built from the same adds, because freeze is the only documented point after which no data may be added and adding the same field several times is supported.
- Actual: The field keeps the read state that the first read built lazily and nothing ever resets (the sorted term ids, the point min/max and 1D order, the sorted doc values, the cached norm): a term or IntPoint added later is silently not found (for example IntPoint p=2, search, p=1: newExactQuery(p, 1) no longer matches), SORTED_NUMERIC values come back as 2, 1, the score uses the old norm, or the search throws ArrayIndexOutOfBoundsException from the corrupted term dictionary (an AssertionError under -ea for some inputs).

## Files

- [src/org/apache/lucene/index/memory/TestMemoryIndexAddAfterSearch.java](src/org/apache/lucene/index/memory/TestMemoryIndexAddAfterSearch.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.memory.TestMemoryIndexAddAfterSearch
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
