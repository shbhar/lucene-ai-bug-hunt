# FastVectorHighlighter with fragCharSize = Integer.MAX_VALUE (or any value above Integer.MAX_VALUE - spanStart) returns the whole field with only the first match highlighted: BaseFragListBuilder computes spanStart + fragCharSize in int, the sum wraps negative, and every later match is silently dropped (SimpleFragListBuilder, the default, and WeightedFragListBuilder)

Bug LFV-401 · Area: highlighter / vectorhighlight BaseFragListBuilder (FastVectorHighlighter, SimpleFragListBuilder, WeightedFragListBuilder) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index the field 'aaaaaaaaaaaaaaaaaaaa foo bbb foo ccc foo ddd' with term vectors (positions and offsets), search foo, and call FastVectorHighlighter.getBestFragments with fragCharSize = Integer.MAX_VALUE to get the whole field, expecting 'aaaaaaaaaaaaaaaaaaaa &lt;b&gt;foo&lt;/b&gt; bbb &lt;b&gt;foo&lt;/b&gt; ccc &lt;b&gt;foo&lt;/b&gt; ddd', which fragCharSize 1000 or Integer.MAX_VALUE - 100 gives. Instead you get 'aaaaaaaaaaaaaaaaaaaa &lt;b&gt;foo&lt;/b&gt; bbb foo ccc foo ddd', with no error: BaseFragListBuilder.createFieldFragList computes the fragment end as spanStart + fragCharSize in int, where spanStart is the first match's start minus the 6-char margin (15 here), so the sum wraps negative and the fragment closes at the end of the first match. The fragment is then re-centred to [0, Integer.MAX_VALUE), which covers the whole field, and every later match is discarded.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: You get 'aaaaaaaaaaaaaaaaaaaa &lt;b&gt;foo&lt;/b&gt; bbb foo ccc foo ddd', with no error: BaseFragListBuilder.createFieldFragList computes the fragment end as spanStart + fragCharSize in int, where spanStart is the first match's start minus the 6-char margin (15 here), so the sum wraps negative and the fragment closes at the end of the first match.

## Files

- [src/org/apache/lucene/search/vectorhighlight/TestFragListBuilderHugeFragCharSize.java](src/org/apache/lucene/search/vectorhighlight/TestFragListBuilderHugeFragCharSize.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/highlighter/build/libs/lucene-highlighter-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.vectorhighlight.TestFragListBuilderHugeFragCharSize
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/overflow-at-scale-2/](../../models/overflow-at-scale-2/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
