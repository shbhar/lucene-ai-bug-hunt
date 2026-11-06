# FieldInfos.hasFreq() returns true for a segment in which no field has term frequencies when the segment holds a field that is not indexed (stored-only, doc-values-only or points-only), because it is computed as indexOptions != DOCS and IndexOptions.NONE passes that test (since 5.0.0, LUCENE-6041)

Finding LFV-1208 · Area: index / FieldInfos aggregate per-segment flags (FieldInfos.hasFreq; the same formula in codecs/perfield PerFieldMergeState.FilterFieldInfos) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1208.html](../../bugs/LFV-1208.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index one document whose only field is a StoredField and look at the segment's FieldInfos: hasPostings() is false, and you expect hasFreq(), documented as 'Returns true if any fields have freqs', to be false as well. Instead it returns true, because the FieldInfos constructor sets the flag for every field whose index options are not DOCS (FieldInfos.java:116), and a field that is not indexed at all (IndexOptions.NONE) passes that test, while the neighbouring hasPostings, hasProx and hasOffsets flags exclude it. The same happens for doc-values-only and points-only fields and for a segment of DOCS-only StringFields plus any such field, so the IndexWriter infoStream says 'freqs' for segments that have none.

## Expected vs actual

- Expected: `hasFreq()` is `false` for a segment whose only field is a `StoredField`, like `hasPostings()`.
- Actual: `hasFreq()` is `true`: `AssertionError` at `assertFalse(fis.hasFreq())` (line 37), with assertions on and off.

## Files

- [src/org/apache/lucene/index/TestFieldInfosHasFreq.java](src/org/apache/lucene/index/TestFieldInfosHasFreq.java) (JUnit test)

## Run it

- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestFieldInfosHasFreq
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestFieldInfosHasFreq.java](TestFieldInfosHasFreq.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
