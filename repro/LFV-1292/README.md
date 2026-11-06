# RangeDocIdStream.intoArray(-1, array), on the stream that LeafCollector.collectRange hands to collect(DocIdStream), copies array.length doc IDs, including IDs past the stream's end and the segment's maxDoc, instead of returning 0, and leaves count() negative, because MathUtil.unsignedMin reads -1 as a huge unsigned bound (since 10.4.0)

Finding LFV-1292 · Area: search / DocIdStream bulk collection (RangeDocIdStream, LeafCollector.collectRange) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1292.html](../../bugs/LFV-1292.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

A collector that overrides LeafCollector.collect(DocIdStream) receives a RangeDocIdStream from the default collectRange(min, max), for example collectRange(0, 5) or a MatchAllDocsQuery over a segment without deletions, and calls stream.intoArray(-1, new int[10]). No doc ID is below -1, so you expect 0 copied docs and an unchanged stream, which is what the same class's forEach(-1) and count(-1), BitSetDocIdStream.intoArray and the test framework's AssertingDocIdStream give or require. Instead it returns 10 and fills docs 0..9, five of which are outside the stream [0, 5) (in a real search, IDs at or above maxDoc), and moves the stream past its end so the next count() is negative, with -ea and -da on main, 10.4.0, 10.5.2 and 10.6.0.

## Expected vs actual

- Expected: `intoArray(-1, ...)` returns 0 and the stream is unchanged.
- Actual: It returns 10 (docs 0..9, five outside the stream): `expected:<0> but was:<10>`, with assertions on and off.

## Files

- [src/org/apache/lucene/search/TestRangeDocIdStreamIntoArrayNegativeUpTo.java](src/org/apache/lucene/search/TestRangeDocIdStreamIntoArrayNegativeUpTo.java) (JUnit test)

## Run it

- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestRangeDocIdStreamIntoArrayNegativeUpTo
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestRangeDocIdStreamIntoArrayNegativeUpTo.java](TestRangeDocIdStreamIntoArrayNegativeUpTo.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
