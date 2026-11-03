# Grouping by a function such as sum(a,b) puts documents without a value into several null groups, one per partial value, because the function value fillers keep the partial value when exists is false (AssertionError with -ea; second-pass NullPointerException since 10.4.0)

Finding LFV-1126 · Area: grouping / function grouping (queries function value fillers: FloatDocValues and siblings; contract in core util.mutable MutableValueFloat) · Priority: low · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-1126.html](../../bugs/LFV-1126.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index two documents with a float doc-values field a = 5 and a = 7 and no field b, and group all documents with GroupingSearch by the function sum(a, b). You expect one null group holding both documents, because sum(a, b) does not exist for either of them and the grouping javadocs put documents without a group value 'in a null group'. You get two groups that both read as null, one per partial sum, because FloatDocValues' value filler stores the partial sum as the value of a MutableValueFloat whose exists flag is false, against MutableValueFloat's documented rule that a missing value must be 0; with -ea the search throws AssertionError in MutableValueFloat.hashCode, and since 10.4.0 a group limit of 1 makes the second pass throw NullPointerException.

## Expected vs actual

- Expected: One group, the null group, with 2 hits.
- Actual: `java.lang.AssertionError: expected:<1> but was:<2>` with assertions off; `AssertionError` at `MutableValueFloat.hashCode(MutableValueFloat.java:67)` with them on.

## Files

- [src/org/apache/lucene/search/grouping/TestGroupByFunctionMissingValues.java](src/org/apache/lucene/search/grouping/TestGroupByFunctionMissingValues.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/grouping/build/libs/lucene-grouping-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.grouping.TestGroupByFunctionMissingValues
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestGroupByFunctionMissingValues.java](TestGroupByFunctionMissingValues.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
