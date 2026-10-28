# FixedShingleFilter takes the gap before each token of a graph route from that token's stream position increment, so tokens stacked right after a hole (a removed stop word followed by a preserved original, a stemmed variant or a synonym) get shingles that skip the filler ('at café' for 'at the café') while the correct ones ('at _ café') are missing, and a token after a multi-position token gets a filler it should not have ('login _' instead of 'login page'); released since 8.0.0

Bug LFV-781 · Area: analysis-common / shingle (FixedShingleFilter, FixedShingleFilterFactory) over core analysis GraphTokenFilter (route walk) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-781.html](../../bugs/LFV-781.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run 'coffee at the café' through WhitespaceTokenizer, StopFilter{the}, ASCIIFoldingFilter(preserveOriginal=true) and FixedShingleFilter(2), and expect the filler that stands for the removed 'the' in every shingle that crosses it, as the folded 'cafe' gets it ('at _'), because the class javadoc says the filter 'handles position increments &gt; 1 by inserting filler tokens' and 'will output stacked shingles' for stacked tokens. Instead the original 'café', stacked behind 'cafe' with position increment 0, is joined straight to 'at': you get [coffee at, at _, at café], at size 3 'coffee at café' appears and 'at _ café' is missing, and on a canned stream a(+1) b(+2) c(+0) swapping only the stream order of the two stacked tokens changes the output from [a _, a c] to [a _, a b]. The same misreading gives 'login _' instead of 'login page' after a two-position token 'login' over 'log' plus a removed 'in', and nothing throws, with assertions on or off.

## Expected vs actual

- Expected: "coffee at the café" gives `[coffee at, at _]`; a, gap, b/c gives `[a _]` whatever the order of b and c; "login" (two positions) then "page" gives "login page".
- Actual: `[coffee at, at _, at café]`; `[a _, a c]` or `[a _, a b]` depending on the stack order; `login _` instead of `login page`. No error.

## Files

- [src/org/apache/lucene/analysis/shingle/TestFixedShingleFilterRouteGaps.java](src/org/apache/lucene/analysis/shingle/TestFixedShingleFilterRouteGaps.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.shingle.TestFixedShingleFilterRouteGaps
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestFixedShingleFilterGapsOnGraphs.java](TestFixedShingleFilterGapsOnGraphs.java)
- [TestFixedShingleFilterRouteGaps.java](TestFixedShingleFilterRouteGaps.java)
- [TestFixedShingleRouteGapV1.java](TestFixedShingleRouteGapV1.java)
- [RouteGapHistoryCheck.java](RouteGapHistoryCheck.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
