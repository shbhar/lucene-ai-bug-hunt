# The default SortedSetDocValues terms enum casts the ordinal to int in seekExact(long) and seekExact(term, TermState): on a view with more than 2^31 values a seek by ordinal throws, and from 2^32 values on it can silently land on the wrong term

Bug LFV-449 · Area: index / sorted-set doc values terms enum (SortedSetDocValuesTermsEnum, the default SortedSetDocValues#termsEnum) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You take the terms enum of a sorted-set doc-values view with more than 2^31 distinct values (for example MultiDocValues.getSortedSetValues over a very large multi-segment index), position it on a term with seekCeil, keep ord or termState, and later return with seekExact(ord) or seekExact(term, state); you expect to land on that term again, because sorted-set ordinals are longs and termsEnum is documented to support ord and seekExact(long). Instead seekExact(long) stores '(int) ord' (SortedSetDocValuesTermsEnum.java:69), so for ordinals from 2^31 to 2^32-1 it asks the values for a negative ordinal and throws, and for 2^32+5 it silently positions on ordinal 5 and returns that term, with -ea and -da. seekCeil, seekExact(BytesRef), next and ord use long arithmetic and are correct; the fix is to drop the cast.

The test shows that the default `SortedSetDocValues#termsEnum()` casts the ordinal to int in `seekExact(long)`, so on a view with more than 2^32 values `seekExact(2^32 + 5)` silently lands on ordinal 5.

## Expected vs actual

Expected: `ord()` is 4294967301 after `seekExact(4294967301)`. Actual: `expected:<4294967301> but was:<5>`, no exception, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/index/TestSortedSetTermsEnumSeekExactLongOrd.java](src/org/apache/lucene/index/TestSortedSetTermsEnumSeekExactLongOrd.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
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
     execute --select-class org.apache.lucene.index.TestSortedSetTermsEnumSeekExactLongOrd
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
