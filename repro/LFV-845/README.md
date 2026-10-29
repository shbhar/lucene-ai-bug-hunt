# Classic QueryParser and flexible StandardQueryParser: a TAB, LF, CR or U+3000 next to a range bound inside [..] or {..} becomes part of the bound, because RANGE_GOOP excludes only U+0020 although both grammars declare these characters as whitespace and skip them in the Range state; such ranges fail to parse or, when the character touches a bracket, silently change their bounds

Bug LFV-845 · Area: queryparser / classic and flexible standard grammars (Range lexical state, RANGE_GOOP token) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-845.html](../../bugs/LFV-845.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You parse 'date:[20200101&lt;TAB&gt;TO&lt;TAB&gt;20201231]' or the multi-line 'date:[&lt;LF&gt;20200101 TO 20201231&lt;LF&gt;]' with the classic or the flexible standard query parser; both grammars declare TAB, LF, CR and U+3000 as whitespace and skip it inside ranges too, so you expect the same query as with spaces. Instead the range-bound token RANGE_GOOP, which excludes only U+0020 and the closing brackets, swallows any of these characters that touches a bound: the first query throws ParseException ('Was expecting: "TO"'), and the second parses without error into a TermRangeQuery whose bounds are '&lt;LF&gt;20200101' and '20201231&lt;LF&gt;', so it also matches 20191231 (4 hits instead of 3). In the same way 'date:{20200101 TO 20201231&lt;TAB&gt;}' admits its own exclusive endpoint, and a leading U+3000 makes the range match nothing.

The test shows that the classic QueryParser keeps a line break that touches a range bracket as part of the bound, although its grammar skips LF as whitespace inside ranges (RANGE_GOOP excludes only U+0020).

## Expected vs actual

Expected `date:[<LF>20200101 TO 20201231<LF>]` to parse like `date:[20200101 TO 20201231]`; actual: the bounds are `<LF>20200101` and `20201231<LF>` (`expected:<date:[20200101 TO 20201231]> but was:<date:[<LF>20200101 TO 20201231<LF>]>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/queryparser/classic/TestRangeBoundWhitespace.java](src/org/apache/lucene/queryparser/classic/TestRangeBoundWhitespace.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:$LUCENE/lucene/queryparser/build/libs/lucene-queryparser-$V.jar
CP=$CP:$LUCENE/lucene/sandbox/build/libs/lucene-sandbox-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queryparser.classic.TestRangeBoundWhitespace
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
