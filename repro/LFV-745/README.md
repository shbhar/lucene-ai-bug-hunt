# MemoryIndex's StoredFields.document offers the visitor fields that have no stored value and asks needsField only once per field name instead of once per stored value, so LazyDocument fails with ArrayIndexOutOfBoundsException or silently loses values, and per-value NO/STOP decisions are ignored

Bug LFV-745 · Area: memory / MemoryIndex stored fields (StoredFieldVisitor protocol) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You add a document with an indexed-only field body (TextField, Store.NO) and a stored field tag with two values t1, t2 to a MemoryIndex and read it through storedFields.document(0, visitor), with a visitor that records its needsField calls or with LazyDocument's lazy-loading pattern from TestLazyDocument, expecting what an IndexWriter index gives for the same document: one needsField(tag) per stored value, never a question about body, and the lazy values tag=t1, tag=t2. Instead MemoryIndex asks about body, which has no stored value, asks about tag only once and then delivers both values on that single YES, because its loop calls needsField once per field name before it checks whether the field has stored values. So LazyDocument's lazy body field throws ArrayIndexOutOfBoundsException when read and tag=t2 is silently missing (with -ea LazyDocument's own inverted assertion, LFV-697, throws instead), and a visitor that wants only the first value of tag gets both.

The test shows that MemoryIndex's `StoredFields.document(int, StoredFieldVisitor)` asks `needsField` about a field with no stored value and only once per field name, not once per stored value.

## Expected vs actual

Expected `[tag, tag]` (body Store.NO, tag stored twice; what an IndexWriter index gives); actual `[body, tag]` (`expected:<[tag, tag]> but was:<[body, tag]>`), with `-ea` and `-da`; passes with the candidate MemoryIndex fix.

## Files

- [src/org/apache/lucene/index/memory/TestMemoryIndexStoredFieldVisitorCalls.java](src/org/apache/lucene/index/memory/TestMemoryIndexStoredFieldVisitorCalls.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.memory.TestMemoryIndexStoredFieldVisitorCalls
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
