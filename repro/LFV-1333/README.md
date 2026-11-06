# FieldInfos.getMergedFieldInfos accepts or rejects the same two indexes depending on leaf order when a field is the soft-deletes field in one index and a regular numeric doc-values field in the other, because FieldInfos.Builder.add checks the soft-deletes role only for the first FieldInfo of a name

Finding LFV-1333 · Area: core / index: FieldInfos.getMergedFieldInfos (merged FieldInfos of a composite reader), FieldInfos.Builder.add existing-name path · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1333.html](../../bugs/LFV-1333.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build index A with IndexWriterConfig.setSoftDeletesField("x") (one document, soft-updated once) and index B with one document holding a plain NumericDocValuesField "x", and call FieldInfos.getMergedFieldInfos on new MultiReader(A, B) and on new MultiReader(B, A). You expect the same outcome for both orders: both accepted, or both rejected as IndexWriter.addIndexes does. Instead (A, B) is accepted and reports x as the soft-deletes field, B's regular field included, while (B, A) throws IllegalArgumentException 'cannot configure [x] as soft-deletes; this index uses [x] as non-soft-deletes already', because FieldInfos.Builder.add checks a name that is already in the builder only with verifySameSchema, which ignores the soft-deletes flag, so the soft-deletes role check runs only for the first FieldInfo of each name.

## Expected vs actual

- Expected: `getMergedFieldInfos` gives the same outcome for `MultiReader(soft, plain)` and `MultiReader(plain, soft)`.
- Actual: `ComparisonFailure: expected:<[accep]ted> but was:<[rejec]ted>`: `(soft, plain)` is accepted, `(plain, soft)` throws `IllegalArgumentException`.

## Files

- [src/org/apache/lucene/index/TestMergedFieldInfosLeafOrder.java](src/org/apache/lucene/index/TestMergedFieldInfosLeafOrder.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestMergedFieldInfosLeafOrder
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestMergedFieldInfosLeafOrder.java](TestMergedFieldInfosLeafOrder.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
