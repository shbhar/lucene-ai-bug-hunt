# FuzzyQuery on the empty term matches only the empty term

Bug LFV-024 · Area: search / multi-term queries · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-024.html](../../bugs/LFV-024.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run a FuzzyQuery whose term text is empty. You expect it to match every indexed term within the edit distance, as the LUCENE-7439 contract says. Instead it matches only the empty term.

## Expected vs actual

- Expected: `"a"~1` -&gt; 5 hits; `""~1` -&gt; 4 hits (`""`, `a`, `b`, `x`); `""~2` -&gt; 5 hits (also `ab`).
- Actual: `""~1` -&gt; 1 hit and `""~2` -&gt; 1 hit, only `""`; the control `"a"~1` gets its 5.

## Files

- [src/TestEmptyTermFuzzyVerify.java](src/TestEmptyTermFuzzyVerify.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main b83101cdd2 (2026-10-23): reproduced.
- Recorded outcome of run 1 on main a9779ef3c193: 9 test(s) run, 6 failed (testEmptyTermConstantScoreRewrite, testEmptyTermDefaultRewrite, testFuzzyTermsEnumDirect, testIntervalsFuzzyTermDisagrees, testSymmetry, testVisitorAutomatonDisagreesWithMatches).
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.
- This repro reads private Lucene state by reflection (setAccessible), so it depends on internals that may change.

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
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class TestEmptyTermFuzzyVerify
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [EmptyFuzzyTerm.java](EmptyFuzzyTerm.java)
- [TestFuzzyQueryEmptyTerm.java](TestFuzzyQueryEmptyTerm.java)
- [test-fuzzyquery.diff](test-fuzzyquery.diff)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
