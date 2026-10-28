# Sort.getPrimarySortField (new in 10.6.0, #16424) skips an index-sort field that the reader's FieldInfos do not list, or whose skip index holds a single value, even when that field still orders the documents (a FilterLeafReader hides it but keeps the index sort, or a BinarySortField derives its key from other fields), so doc-values range queries on the next sort field, LongField.newRangeQuery included, return wrong hits or throw

Bug LFV-860 · Area: search / index-sort range-query shortcuts (Sort.getPrimarySortField; SortedNumericDocValuesRangeQuery, SortedSetDocValuesRangeQuery, IndexSortSortedNumericDocValuesRangeQuery) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-860.html](../../bugs/LFV-860.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You sort an index by two fields, say (c, f), and then either read it through a FilterLeafReader that hides c from FieldInfos but, like every FilterLeafReader by default, still reports the index sort (c, f) in getMetaData, or sort by a BinarySortField subclass whose key is derived from another doc-values field, as its javadoc allows; a doc-values range query on f should still match exactly the documents whose f is in the range, as it does on 10.5.2. On main and the 10.6.0 release candidate, Sort.getPrimarySortField (added by #16424) finds no FieldInfo for c (or for the BinarySortField's own name), concludes that sorting by it 'has no effect', and returns f as the primary sort, so the range queries' index-sort shortcuts binary-search a segment that is not ordered by f. The result: most ranges miss matching documents and some return documents outside the range (43 of 55 ranges wrong in an independent test; f:[1 TO 3] returns 0 hits instead of 2), and LongField.newRangeQuery can throw IllegalArgumentException 'minDoc must be &lt; maxDoc'.

## Expected vs actual

- Expected: 2 hits for f:[1 TO 3] in all three tests (the documents with f = 1 and f = 3), as the plain reader and 10.5.2 return.
- Actual: 0 hits in the two doc-values tests (`getPrimarySortField` reports f), and `IllegalArgumentException: minDoc must be < maxDoc but got minDoc=2 maxDoc=0` from `LongField.newRangeQuery`.

## Files

- [src/org/apache/lucene/search/TestPrimarySortFieldHiddenSortField.java](src/org/apache/lucene/search/TestPrimarySortFieldHiddenSortField.java) (JUnit test)
- [src/org/apache/lucene/search/TestPrimarySortFieldDerivedBinaryKey.java](src/org/apache/lucene/search/TestPrimarySortFieldDerivedBinaryKey.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.TestPrimarySortFieldHiddenSortField \
       --select-class org.apache.lucene.search.TestPrimarySortFieldDerivedBinaryKey
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestPrimarySortFieldSkipsOrderingField.java](TestPrimarySortFieldSkipsOrderingField.java)
- [spi-registration.diff](spi-registration.diff)
- [branch_10x-variant.diff](branch_10x-variant.diff)
- [TestPrimarySortFieldHiddenSortField.java](TestPrimarySortFieldHiddenSortField.java)
- [TestPrimarySortFieldDerivedBinaryKey.java](TestPrimarySortFieldDerivedBinaryKey.java)
- [TestHiddenSortFieldRange.java](TestHiddenSortFieldRange.java)
- [TestDerivedBinarySortKey.java](TestDerivedBinarySortKey.java)

1 other file(s) of the recorded repro are not exported.

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
