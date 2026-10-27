# MultiPassIndexSplitter builds every output part with a bare IndexWriterConfig and accepts no config, so it cannot split any index that uses soft deletes (7.4.0+) or has a parent field (9.10.0+): split and its command line throw IllegalArgumentException before any part gets a document

Bug LFV-618 · Area: misc / index tools (MultiPassIndexSplitter.split, output IndexWriterConfig); IndexWriter.addIndexes field-role checks · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You split an index with MultiPassIndexSplitter, for example one written with IndexWriterConfig#setSoftDeletesField("soft") and a single softUpdateDocument call, or one written with IndexWriterConfig#setParentField("_parent"), and expect it to split like any other index: the split javadoc says the source 'can have deletions, can have multiple segments (or multiple readers)', and the only documented limitation is that document blocks may be broken up. Instead split opens every part's IndexWriter with new IndexWriterConfig(null), which names no soft-deletes or parent field and cannot be replaced by the caller, so IndexWriter.addIndexes rejects the input's field infos with IllegalArgumentException ('this index has [soft] as soft-deletes already but soft-deletes field is not configured in IWC', or 'can't add field [_parent] as parent document field; this IndexWriter has no parent document field configured') and nothing is split.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: split opens every part's IndexWriter with new IndexWriterConfig(null), which names no soft-deletes or parent field and cannot be replaced by the caller, so IndexWriter.addIndexes rejects the input's field infos with IllegalArgumentException ('this index has [soft] as soft-deletes already but soft-deletes field is not configured in IWC', or 'can't add field [_parent] as parent document field; this IndexWriter has no parent document field configured') and nothing is split.

## Files

- [src/org/apache/lucene/misc/index/TestMultiPassIndexSplitterIndexSettings.java](src/org/apache/lucene/misc/index/TestMultiPassIndexSplitterIndexSettings.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/misc/build/libs/lucene-misc-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.misc.index.TestMultiPassIndexSplitterIndexSettings
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
