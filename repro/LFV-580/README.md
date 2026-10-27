# IndexWriterConfig setters called after the IndexWriter was created still change that writer (index sort, parent field, soft-deletes field, codec, similarity,...), although the class javadoc says such changes have no effect; a late index sort or parent field makes merges fail and closes the writer, and a late soft-deletes field can drop a live document

Bug LFV-580 · Area: index / IndexWriterConfig and IndexWriter configuration (creation-only settings re-read from the caller's config object) · Priority: medium · Impact: index unusable · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You create an IndexWriter from an IndexWriterConfig and later call a setter such as setIndexSort, setParentField, setSoftDeletesField or setCodec on that same config object, expecting no effect, because the IndexWriterConfig class javadoc says that once the writer has been created 'changes to this object will not affect the IndexWriter instance'. Instead IndexWriter has kept the caller's object since 4.9 and reads these settings again for every new segment, so new segments get the new sort, parent field, soft-deletes field or codec. forceMerge then fails with 'cannot change index sort from &lt;long: "a"&gt; to &lt;long: "b"&gt;' or 'expected parent doc field to be "_parent " across all segments' and closes the writer, and with a late soft-deletes field a document that was never deleted vanishes after an unrelated delete (numDocs 0, expected 1).

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: IndexWriter has kept the caller's object since 4.9 and reads these settings again for every new segment, so new segments get the new sort, parent field, soft-deletes field or codec. forceMerge then fails with 'cannot change index sort from &lt;long: "a"&gt; to &lt;long: "b"&gt;' or 'expected parent doc field to be "_parent " across all segments' and closes the writer, and with a late soft-deletes field a document that was never deleted vanishes after an unrelated delete (numDocs 0, expected 1).

## Files

- [src/org/apache/lucene/index/TestIndexWriterConfigChangeAfterCreation.java](src/org/apache/lucene/index/TestIndexWriterConfigChangeAfterCreation.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestIndexWriterConfigChangeAfterCreation
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
