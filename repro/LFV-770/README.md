# DoubleValuesSource#toSortableLongDoubleValuesSource loses its NumericUtils.doubleToSortableLong encoding when rewritten (rewrite returns the plain toLongValuesSource cast adapter), so values read after the documented rewrite step, and sorts on it through IndexSearcher, use (long) casts instead

Bug LFV-770 · Area: core / search value sources (DoubleValuesSource, LongValuesSource adapters) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-770.html](../../bugs/LFV-770.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You turn a double source into a long source with DoubleValuesSource#toSortableLongDoubleValuesSource, documented as 'Convert to LongValuesSource by calling NumericUtils#doubleToSortableLong', and, as the LongValuesSource javadoc tells clients to, call rewrite(IndexSearcher) before getValues, or you sort on its getSortField, which IndexSearcher rewrites for you. You expect the sortable-long encoding, which keeps the order of the doubles; instead rewrite returns the plain toLongValuesSource adapter (its toString changes from sortableLong(...) to long(...)), so 0.75, 0.25 and -0.5 all become 0, 2.5 becomes 2 and NaN becomes 0, and a sort over the values {0.75, 0.25, -0.5, 2.5} comes back in docID order with keys 0, 0, 0, 2. The source is correct when it is not rewritten, and the one-line fix is to return inner.rewrite(searcher).toSortableLongDoubleValuesSource.

## Expected vs actual

- Expected: After `rewrite`, 0.75 still gives 4604930618986332160, and the sort returns docs 2, 1, 0, 3.
- Actual: The rewritten source prints `long(double(price))` and gives 0, 0, 0, 2; the sort returns docs 0, 1, 2, 3 with keys 0, 0, 0, 2.

## Files

- [src/org/apache/lucene/search/TestSortableLongDoubleValuesSourceRewrite.java](src/org/apache/lucene/search/TestSortableLongDoubleValuesSourceRewrite.java) (JUnit test)

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

# run 1: assertions on, seed 5EED257
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=5EED257 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestSortableLongDoubleValuesSourceRewrite
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestSortableLongDoubleValuesSourceRewrite.java](TestSortableLongDoubleValuesSourceRewrite.java)
- [TestSortableLongSortOracle.java](TestSortableLongSortOracle.java)
- [TestSortableLongRewrite.java](TestSortableLongRewrite.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
