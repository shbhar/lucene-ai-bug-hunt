# MemoryIndex keeps a SORTED or BINARY doc value as a shallow BytesRef clone of the caller's bytes, so if the caller later overwrites its byte array the value the MemoryIndex reports silently changes (regression of LUCENE-10405, since 9.1.0)

Finding LFV-1112 · Area: memory / MemoryIndex doc values (addField and fromDocument: BINARY and SORTED value storage) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You add new SortedDocValuesField("color", new BytesRef(buf)) with buf holding "red" to a MemoryIndex, then re-use buf for "tan" after addField has returned, and expect the MemoryIndex to keep reporting "red", as IndexWriter does and as MemoryIndex did up to 9.0.0. It reports "tan", with no error: since 9.1.0 MemoryIndex stores BINARY and SORTED doc values as BytesRef.clone(), which BytesRef documents as a shallow copy that shares the caller's byte array. SORTED_SET values, indexed terms, points and vectors are still copied into the MemoryIndex's own storage.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the reported behaviour is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/index/memory/TestMemoryIndexSortedDocValueCopy.java](src/org/apache/lucene/index/memory/TestMemoryIndexSortedDocValueCopy.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
- Tested on Lucene main e1faeba08bde (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed 5EED0D05
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=3 -Dtests.seed=5EED0D05 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.memory.TestMemoryIndexSortedDocValueCopy
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
