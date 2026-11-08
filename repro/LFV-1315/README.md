# RegExp(String, int, int) validates its two flag words with signed range comparisons instead of bit masks, so a syntax bit in match_flags next to a match bit (CASE_INSENSITIVE | INTERSECTION) or a negative flag word is accepted, and its stray bits take effect, instead of IllegalArgumentException (every release since 8.7.0)

Finding LFV-1315 · Area: core / util/automaton: RegExp constructor flag-word validation (also reached through RegexpQuery) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1315.html](../../bugs/LFV-1315.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build new RegExp("a&amp;b", RegExp.NONE, RegExp.CASE_INSENSITIVE | RegExp.INTERSECTION), with the syntax flag INTERSECTION put into the match-flags word by mistake. You expect IllegalArgumentException 'Illegal match flag', which the constructor throws for new RegExp("a&amp;b", RegExp.NONE, RegExp.INTERSECTION), the same syntax bit alone. Instead no exception is thrown and '&amp;' is parsed as intersection although syntax_flags is NONE, because the checks compare ranges ('match_flags &gt; 0 &amp;&amp; match_flags &lt;= ALL', 'syntax_flags &gt; ALL') instead of testing bits, so a match bit or the sign bit hides the stray bits, which then take effect through 'flags = syntax_flags | match_flags' (likewise new RegExp("a", -1) is accepted and matches "A").

## Expected vs actual

- Expected: `IllegalArgumentException: Illegal match flag`, as for `INTERSECTION` alone in the match word.
- Actual: `AssertionFailedError: Expected exception IllegalArgumentException but no exception was thrown`: the word is accepted.

## Files

- [src/org/apache/lucene/util/automaton/TestRegExpSyntaxBitInMatchFlags.java](src/org/apache/lucene/util/automaton/TestRegExpSyntaxBitInMatchFlags.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
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
     execute --select-class org.apache.lucene.util.automaton.TestRegExpSyntaxBitInMatchFlags
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestRegExpSyntaxBitInMatchFlags.java](TestRegExpSyntaxBitInMatchFlags.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
