# SimplePatternTokenizer's String constructors do not determinize the regexp (and ignore determinizeWorkLimit), so valid patterns such as ab|ac or http|https throw 'please determinize the incoming automaton first'

Finding LFV-526 · Area: analysis-common / pattern tokenizers (SimplePatternTokenizer String constructors) · Priority: medium · Impact: crash · Novelty: known outside Lucene

AI-assisted finding, confirmed by automated checks. Upstream status: Fix in progress, by John Wagster (john-wagster; issue #16999 and open PR #17001, an independent report and fix, later than our listing) on 2026-11-06 (https://github.com/apache/lucene/pull/17001). Issue #16999 (john-wagster, 2026-11-06) reports this defect, found through a downstream simple_pattern tokenizer (elastic/elasticsearch#164857, 2026-11-05). Open PR #17001 (same author and day; no milestone, CI green and approved by rmuir on 2026-11-16) changes SimplePatternTokenizer, SimplePatternSplitTokenizer and their two factories; it removes both tokenizers' three-argument String constructor and the factories' determinizeWorkLimit argument. Neither cites this list. rmuir (Member) commented on 2026-11-12; the author replied on 2026-11-14 and pushed a second commit on 2026-11-16. Sent through the one-argument String constructor, the repro's two patterns return the expected tokens on a build of that commit and fail on main 781025d6fe32, with and without assertions. The recorded repro uses the three-argument constructor, which the PR removes, so it does not compile against that build. This site files nothing upstream.

## What it shows

You build a tokenizer with new SimplePatternTokenizer("ab|ac") (or the three-argument String constructor with a determinizeWorkLimit) and expect a working tokenizer for any valid RegExp, as the constructors' javadoc ('See RegExp for the accepted syntax'), the sibling SimplePatternSplitTokenizer and both factories give you. Instead the constructor throws IllegalArgumentException('please determinize the incoming automaton first'): it passes new RegExp(regexp).toAutomaton() unchanged to the Automaton constructor, which rejects non-deterministic automata, and since Lucene 10.0 (LUCENE-10010) RegExp no longer determinizes its result. The determinizeWorkLimit argument is never used.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The constructor throws IllegalArgumentException('please determinize the incoming automaton first'): it passes new RegExp(regexp).toAutomaton() unchanged to the Automaton constructor, which rejects non-deterministic automata, and since Lucene 10.0 (LUCENE-10010) RegExp no longer determinizes its result.

## Files

- [src/org/apache/lucene/analysis/pattern/TestSimplePatternTokenizerStringCtor.java](src/org/apache/lucene/analysis/pattern/TestSimplePatternTokenizerStringCtor.java) (JUnit test)

## Run it

- Tested on Lucene main 7b8418faa2ff (a commit of 2026-11-20): still reproduces.
- Tested on Lucene main ea57360912ed (a commit of 2026-10-24): reproduced.
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
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.pattern.TestSimplePatternTokenizerStringCtor
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
