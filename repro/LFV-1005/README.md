# matchhighlight PassageSelector sizes its passage queue by maxPassages before it looks at any match, so maxPassages = Integer.MAX_VALUE ('all passages') throws IllegalArgumentException and a large maxPassages allocates maxPassages + 1 queue slots per call, even for a text with two matches

Bug LFV-1005 · Area: highlighter / matchhighlight passage selection (PassageSelector.pickBest passage-queue sizing; used by MatchHighlighter through FieldValueHighlighters.highlighted, which passes maxPassages through unchecked) · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call PassageSelector.pickBest on a short text with two matches (or MatchHighlighter with FieldValueHighlighters.highlighted) and pass maxPassages = Integer.MAX_VALUE to mean 'all passages', and you expect the same two passages that maxPassages = 10 returns, because maxPassages only caps the result (the method clamps its result to it) and there can never be more passages than matches. Instead pickBest builds its priority queue with Math.max(16, maxPassages) slots before it reads any match, so the call throws IllegalArgumentException 'maxSize must be &gt;= 0 and &lt; 2147483631; got: 2147483647'. A smaller but still large maxPassages succeeds and allocates memory in proportion to maxPassages rather than to the matches: about 64 MiB per call at 2^24.

The test shows that matchhighlight `PassageSelector.pickBest` sizes its priority queue by `maxPassages` before reading any match, so `maxPassages = Integer.MAX_VALUE` ("all passages") fails.

## Expected vs actual

Expected: the one passage around the single match in "lucene search"; actual (-ea and -da): `IllegalArgumentException: maxSize must be >= 0 and < 2147483631; got: 2147483647` from `PriorityQueue.<init>` via `PassageSelector.pickBest:96`.

## Files

- [src/org/apache/lucene/search/matchhighlight/TestPassageSelectorMaxValueMaxPassages.java](src/org/apache/lucene/search/matchhighlight/TestPassageSelectorMaxValueMaxPassages.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/highlighter/build/libs/lucene-highlighter-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.matchhighlight.TestPassageSelectorMaxValueMaxPassages
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
