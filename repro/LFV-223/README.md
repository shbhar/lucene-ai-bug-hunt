# ToParentBlockJoinSortField on a STRING field with max-child selection and childMissingValue=STRING_LAST throws IndexOutOfBoundsException when a parent has children both with and without the field

Bug LFV-223 · Area: join / ToParentBlockJoinSortField string sort (ToParentDocValues missing-child ordinals, since #15727) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You sort parent documents by a string field of their children, descending, with new ToParentBlockJoinSortField("s", STRING, reverse=true, null, SortField.STRING_LAST, parents, children) (or the 8-argument form with reverseChildren=true), and expect the search to return the parents with children that lack the field sorted as missing-last, because the javadoc lists STRING_LAST as a legal child missing value that 'participates in the min/max selection among siblings'. Instead, as soon as the search collects a parent that has one child with the field and another child without it, it throws IndexOutOfBoundsException from the doc-values terms dictionary (Lucene90DocValuesProducer$TermsDict.seekExact via ToParentDocValues$SortedDVs.lookupOrd), with -ea and -da. The cause is that the block-join wrapper reports 'a missing child, sorted last, won the max' as the ordinal Integer.MAX_VALUE, and the string comparator TermOrdValComparator treats every ordinal other than -1 as a real one and looks it up.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: As soon as the search collects a parent that has one child with the field and another child without it, it throws IndexOutOfBoundsException from the doc-values terms dictionary (Lucene90DocValuesProducer$TermsDict.seekExact via ToParentDocValues$SortedDVs.lookupOrd), with -ea and -da.

## Files

- [src/org/apache/lucene/search/join/TestBlockJoinSortStringLastReverse.java](src/org/apache/lucene/search/join/TestBlockJoinSortStringLastReverse.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/join/build/libs/lucene-join-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.join.TestBlockJoinSortStringLastReverse
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
