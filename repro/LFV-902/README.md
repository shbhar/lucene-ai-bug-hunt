# LMDirichletSimilarity accepts mu = 0, but then every document scores 0: log(1 + tf/(mu*P)) + log(mu/(dl+mu)) becomes +Infinity + -Infinity = NaN, and the 'score &gt; 0' clamp turns NaN into 0

Bug LFV-902 · Area: search / similarities (LMDirichletSimilarity) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure LMDirichletSimilarity with mu = 0, which both constructors accept ('must be a non-negative finite value') and which upstream's TestLMDirichletSimilarity uses as its 'minimum value'. You expect documents that contain the query term more often than the collection model predicts to get a positive score and be ranked, as they are for every mu &gt; 0 down to Float.MIN_VALUE, where the score already equals its finite mu -&gt; 0 limit log(tf/(dl*P)). Instead score computes log(1 + tf/0) + log(0/dl) = +Infinity + -Infinity = NaN, the 'score &gt; 0 ? score: 0' clamp returns 0.0, and every hit scores 0.0, so the best match can come back last; explain shows 'Infinity = term weight' and '-Infinity = document norm' under a 0.0 total.

## Expected vs actual

- Expected: You expect documents that contain the query term more often than the collection model predicts to get a positive score and be ranked, as they are for every mu &gt; 0 down to Float.MIN_VALUE, where the score already equals its finite mu -&gt; 0 limit log(tf/(dl*P)).
- Actual: score computes log(1 + tf/0) + log(0/dl) = +Infinity + -Infinity = NaN, the 'score &gt; 0 ? score: 0' clamp returns 0.0, and every hit scores 0.0, so the best match can come back last; explain shows 'Infinity = term weight' and '-Infinity = document norm' under a 0.0 total.

## Files

- [src/org/apache/lucene/search/similarities/TestLMDirichletMuZero.java](src/org/apache/lucene/search/similarities/TestLMDirichletMuZero.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.similarities.TestLMDirichletMuZero
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/lmdirichlet-mu-zero-limit-and-multisimilarity-norm-combsum/](../../models/lmdirichlet-mu-zero-limit-and-multisimilarity-norm-combsum/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
