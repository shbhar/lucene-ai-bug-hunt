# SpanMultiTermQueryWrapper.FrequentTermsSpanBooleanQueryRewrite (new in 10.6.0) sizes its priority queue from maxSize before collecting any term, so maxSize &gt;= 2147483631 (for example Integer.MAX_VALUE as 'no limit') makes every rewrite throw IllegalArgumentException, and a large maxSize allocates a (maxSize + 1)-slot array on every rewrite

Bug LFV-944 · Area: queries / spans (SpanMultiTermQueryWrapper.FrequentTermsSpanBooleanQueryRewrite queue sizing; core ScoringRewrite creates the top-level builder before collecting terms; util PriorityQueue pre-allocates maxSize + 1 slots) · Priority: low · Impact: crash · Novelty: new

Bug page: [../../bugs/LFV-944.html](../../bugs/LFV-944.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You wrap a PrefixQuery in SpanMultiTermQueryWrapper, call setRewriteMethod(new FrequentTermsSpanBooleanQueryRewrite(Integer.MAX_VALUE)), whose javadoc promises only that it 'keeps at most maxSize terms' (ranked by document frequency instead of boost, unlike TopTermsSpanBooleanQueryRewrite), and expect it to keep all 3 matching terms, as TopTermsSpanBooleanQueryRewrite(Integer.MAX_VALUE) does on the same index. Instead the constructor accepts the value and every search then throws IllegalArgumentException 'maxSize must be &gt;= 0 and &lt; 2147483631; got: 2147483647', because getTopLevelBuilder returns PriorityQueue.usingComparator(maxSize, order) and ScoringRewrite.rewrite calls it before it collects any term, so the internal queue pre-allocates maxSize + 1 slots (or rejects a size it cannot allocate) instead of being sized by the terms actually found; for the same reason a large legal cap such as 2^26 allocates about 268 MB per rewrite for 3 terms. Sizing the queue lazily, by min(maxSize, collected terms), as the sibling TopTermsRewrite does with a growing queue, fixes both and leaves every result unchanged.

The test shows that SpanMultiTermQueryWrapper with FrequentTermsSpanBooleanQueryRewrite(Integer.MAX_VALUE) cannot be searched at all, because its PriorityQueue is sized by maxSize before any term is collected.

## Expected vs actual

Expected: the 3 matching terms (aa1, aa2, aa3) are kept and the count is 3. Actual (-ea and -da): IllegalArgumentException 'maxSize must be &gt;= 0 and &lt; 2147483631; got: 2147483647' from the rewrite.

## Files

- [src/org/apache/lucene/queries/spans/TestFrequentTermsSpanRewriteMaxSize.java](src/org/apache/lucene/queries/spans/TestFrequentTermsSpanRewriteMaxSize.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.spans.TestFrequentTermsSpanRewriteMaxSize
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/frequent-terms-span-rewrite-size-edges-16484/](../../models/frequent-terms-span-rewrite-size-edges-16484/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
