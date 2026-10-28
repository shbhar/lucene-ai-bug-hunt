# RegExp numeric interval &lt;n-m&gt; accepts a leading '+' on a bound and counts it as a digit, so &lt;+1-+9&gt; matches 01..09 instead of 1..9 and &lt;+100-999&gt; loses its fixed width

Bug LFV-813 · Area: util / automaton · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run a RegexpQuery, or a classic QueryParser query /&lt;+1-+9&gt;/, on a field with the terms 5 and 05. The RegExp javadoc says interval bounds are non-negative decimal integers and that bounds with the same number of digits fix the length of the matching strings, so you expect either an 'interval syntax error' (as for '-1', ' 1' or an overflowing bound) or the meaning of &lt;1-9&gt;, which matches 5. Instead the parser accepts the '+' and counts it as a digit, so &lt;+1-+9&gt; matches only 01 to 09 and returns 05; &lt;+1-9&gt; and &lt;+100-999&gt; lose their fixed width and accept any number of leading zeros, and &lt;+100-+999&gt; means 0100 to 0999.

## Expected vs actual

- Expected: You expect either an 'interval syntax error' (as for '-1', ' 1' or an overflowing bound) or the meaning of &lt;1-9&gt;, which matches 5.
- Actual: The parser accepts the '+' and counts it as a digit, so &lt;+1-+9&gt; matches only 01 to 09 and returns 05; &lt;+1-9&gt; and &lt;+100-999&gt; lose their fixed width and accept any number of leading zeros, and &lt;+100-+999&gt; means 0100 to 0999.

## Files

- [src/org/apache/lucene/util/automaton/TestRegExpIntervalBoundSyntax.java](src/org/apache/lucene/util/automaton/TestRegExpIntervalBoundSyntax.java) (JUnit test)

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
     execute --select-class org.apache.lucene.util.automaton.TestRegExpIntervalBoundSyntax
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
