# SpanContainingQuery and SpanWithinQuery miss documents and spans when the little query's spans end out of order (for example a SpanOrQuery of a longer near clause and a shorter clause that can occur inside it): their single forward pass tests only the current little span (containing) or never goes back to a big span it skipped (within), so a later, shorter little span inside a big span is never tried

Bug LFV-909 · Area: queries / span queries (per-document lockstep of the ContainSpans subclasses in SpanContainingQuery and SpanWithinQuery) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-909.html](../../bugs/LFV-909.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index 'a b a' and search with SpanContainingQuery(big = SpanNear([a, b], slop 0), little = SpanOr(SpanNear([a, a], slop 5), b)): the big span 'a b' at [0,2) contains the little span 'b' at [1,2), so you expect a hit, and SpanWithinQuery with the same pair should match too. Instead both return nothing, because the little spans arrive sorted by start as [0,3) and then [1,2) (their ends go down, which the Spans contract allows), and each query's single forward pass gives up after testing only the longer little span [0,3): containing moves on to the next big span, within moves the big iterator past [0,2) and never comes back. The same happens in a sentence-scoped search for '(apple NEAR/5 pie) OR cake' on 'bos apple cake eos bos pie eos' (the 'cake' sentence is missed only because 'apple... pie' crosses into the next sentence), so adding a word to a document or an OR alternative to the query can remove a hit; nothing extra is ever returned.

## Expected vs actual

- Expected: `containing=1 within=1` in every check: the sentence still contains `cake`, and `new york` contains `york`.
- Actual: `containing=0 within=0` for `bos apple cake eos bos pie eos` and for `new york city` with the OR alternative, and `containing=1 within=0` with big `york`; the two controls give 1 and 1.

## Files

- [src/org/apache/lucene/queries/spans/TestSpanContainNonMonotoneLittle.java](src/org/apache/lucene/queries/spans/TestSpanContainNonMonotoneLittle.java) (JUnit test)
- [src/SpanContainMin.java](src/SpanContainMin.java) (main() class)
- [src/SpanContainMin5.java](src/SpanContainMin5.java) (main() class)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual); a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.spans.TestSpanContainNonMonotoneLittle

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" SpanContainMin
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" SpanContainMin5
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestSpanContainOutOfOrderLittleEnds.java](TestSpanContainOutOfOrderLittleEnds.java)
- [fix-recorded-freq.diff](fix-recorded-freq.diff)
- [TestSpanContainNonMonotoneLittle.java](TestSpanContainNonMonotoneLittle.java)
- [TestContainWithinOutOfOrderEnds.java](TestContainWithinOutOfOrderEnds.java)
- [TestContainmentMonotoneUnion.java](TestContainmentMonotoneUnion.java)

Formal models and scripts this bug's part of the hunt used: [../../models/span-containing-within-lockstep-misses-nested-little-spans/](../../models/span-containing-within-lockstep-misses-nested-little-spans/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
