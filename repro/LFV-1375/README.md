# PerFieldMergeState.FilterFieldInfos, the FieldInfos view that per-field doc-values and postings delegates get at merge time, restricts every has* flag, size(), iteration and fieldInfo(..) to the delegate's fields except hasVectorValues(), which still reports the whole segment, so it returns true when none of the view's fields has vectors (every release since 9.0.0)

Finding LFV-1375 · Area: codecs / per-field formats (PerFieldMergeState.FilterFieldInfos: the merge-time restricted FieldInfos of PerFieldDocValuesFormat and PerFieldPostingsFormat) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1375.html](../../bugs/LFV-1375.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You give the codec a custom doc-values format through getDocValuesFormatForField whose merge looks at mergeState.mergeFieldInfos, index two documents (one commit each) that each have a numeric doc-values field 'd' and a float vector field 'v', and call forceMerge(1). The view your format gets contains only 'd' (size() is 1, iteration and fieldInfo(..) see only 'd', and the nine other has* flags are recomputed over 'd' alone), so you expect hasVectorValues(), documented as 'Returns true if any fields have vector values', to be false. Instead it returns true, inherited from the segment's field 'v', because FilterFieldInfos computes its base flags over every field of the segment and overrides every has* method except hasVectorValues(), which FieldInfos gained in 9.0.0 after the view was written; a postings delegate under a PerFieldPostingsFormat sees the same.

## Expected vs actual

- Expected: `hasVectorValues()` on the postings delegate's merge view (one field, `id`) is `false`.
- Actual: `true`: `AssertionError: expected:<[false]> but was:<[true]>`, with assertions on and off.

## Files

- [src/org/apache/lucene/codecs/perfield/TestPerFieldMergeHasVectorValues.java](src/org/apache/lucene/codecs/perfield/TestPerFieldMergeHasVectorValues.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.perfield.TestPerFieldMergeHasVectorValues
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestPerFieldMergeHasVectorValues.java](TestPerFieldMergeHasVectorValues.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
