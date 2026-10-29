# BulkScorer#score's javadoc example ('a Scorer-based implementation could look like below') calls scorer.advance(min) and scorer.nextDoc, which Scorer has not had since LUCENE-6919 made it expose iterator instead of extending DocIdSetIterator, so the example does not compile on any release since 5.5.0 (documentation defect)

Bug LFV-1026 · Area: core / search (BulkScorer#score javadoc example) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

The javadoc of BulkScorer#score(LeafCollector, Bits, int, int) explains the window contract (ignore documents before min, return a value &gt;= max) and then shows 'a Scorer-based implementation could look like below'. Copied into code on any release since 5.5.0 the example does not compile, because it calls scorer.advance(min) and scorer.nextDoc: Scorer stopped extending DocIdSetIterator in LUCENE-6919 and now offers those methods only through scorer.iterator. With scorer.iterator.advance(min) and scorer.iterator.nextDoc the example compiles and honours the contract, so only the javadoc is wrong.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/search/TestBulkScorerJavadocExample.java](src/org/apache/lucene/search/TestBulkScorerJavadocExample.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
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
     execute --select-class org.apache.lucene.search.TestBulkScorerJavadocExample
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
