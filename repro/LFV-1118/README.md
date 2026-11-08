# ConcatenateGraphFilter without a token separator (and so CompletionAnalyzer with preserveSep=false) silently drops a concatenated path around a position hole, because Automaton.addEpsilon copies from a dest state that its own first addTransition sorts and reduces in place (AssertionError with -ea)

Finding LFV-1118 · Area: analysis-common / ConcatenateGraphFilter no-separator mode, and core / util.automaton (Automaton.addEpsilon); reached from suggest (CompletionAnalyzer preserveSep=false, document suggester) · Priority: low · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-1118.html](../../bugs/LFV-1118.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You concatenate the token graph play | thewho (spanning two positions) | who, which a multi-word synonym ('the who' =&gt; 'thewho') followed by a stop filter produces for 'play the who', with ConcatenateGraphFilter and no separator (a null tokenSeparator, or CompletionAnalyzer with preserveSep=false), and expect one token per path, 'playthewho' and 'playwho', as the class javadoc promises. With assertions disabled you get only 'playwho', so a suggestion reached through the synonym (prefix 'playth') is silently missing; with -ea the filter throws an AssertionError in Automaton.getNextTransition. The cause is Automaton.addEpsilon: it copies the transitions of a state that is still being built, and its own first addTransition to the other state sorts and shrinks that state in place mid-copy, so some transitions are copied twice and others never.

## Expected vs actual

- Expected: two tokens: `playthewho`, then `playwho`.
- Actual: assertions off: one token, `playwho` (`term 0 expected:<play[the]who> but was:<play[]who>`); assertions on: `AssertionError` at `Automaton.getNextTransition(Automaton.java:493)`.

## Files

- [src/org/apache/lucene/analysis/miscellaneous/TestConcatenateGraphFilterHoleWithoutSeparator.java](src/org/apache/lucene/analysis/miscellaneous/TestConcatenateGraphFilterHoleWithoutSeparator.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx2g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.miscellaneous.TestConcatenateGraphFilterHoleWithoutSeparator
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestConcatenateGraphFilterHoleWithoutSeparator.java](TestConcatenateGraphFilterHoleWithoutSeparator.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
