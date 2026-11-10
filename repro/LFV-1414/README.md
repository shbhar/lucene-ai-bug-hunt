# The merge-time FieldInfos view given to per-field doc-values and postings delegates (PerFieldMergeState.FilterFieldInfos) throws NullPointerException from fieldInfo(int) for a field number the wrapped segment does not have, where FieldInfos#fieldInfo(int) documents a null result (every release since 6.3.0)

Finding LFV-1414 · Area: codecs / per-field formats (PerFieldMergeState.FilterFieldInfos: the merge-time restricted FieldInfos of PerFieldDocValuesFormat and PerFieldPostingsFormat) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1414.html](../../bugs/LFV-1414.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You plug a custom doc-values (or postings) format into the codec as a per-field delegate, and its merge looks up, in a source segment's FieldInfos (mergeState.fieldInfos[i]), the number of a merged field that this segment does not have, for example field 'b' when the first segment was committed before any document had 'b'. FieldInfos#fieldInfo(int) documents 'the FieldInfo object or null when the given fieldNumber doesn't exist', and the same view's fieldInfo(String) returns null for 'b', so you expect null. Instead the per-field merge view reads res.name on the null result and throws NullPointerException ('Cannot read field "name" because "res" is null'), which fails the merge unless your format catches it.

## Expected vs actual

- Expected: The lookup returns null (segment 1 has no field number 1); the test sees `[null]` and passes.
- Actual: `NullPointerException: Cannot read field "name" because "res" is null` at `PerFieldMergeState.java:217`, out of `forceMerge(1)`, with assertions on and off.

## Files

- [src/org/apache/lucene/codecs/perfield/TestPerFieldMergeFieldInfoByNumber.java](src/org/apache/lucene/codecs/perfield/TestPerFieldMergeFieldInfoByNumber.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): reproduced.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.perfield.TestPerFieldMergeFieldInfoByNumber
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestPerFieldMergeFieldInfoByNumber.java](TestPerFieldMergeFieldInfoByNumber.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
