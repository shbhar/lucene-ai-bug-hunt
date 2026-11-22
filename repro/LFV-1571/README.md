# On Lucene 3.4.0 to 10.6.0 SynonymMap.Builder.build() can hand its keys to the FST out of order when one key holds an unpaired surrogate: the synonym rule of another, well-formed word can then never fire in the map that build() returns, with no error when assertions are off, or build() can throw ArrayIndexOutOfBoundsException (run: five one-word rules behind a filter that cuts every word to its first UTF-16 unit, the stock TruncateTokenFilter from 4.8.0 on, fail with and without assertions on each of the 136 releases that were run; a second set of five rules throws with assertions off on each of the 55 releases from 8.4.0; branch_11x and main, both unreleased, no longer have that code)

Finding LFV-1571 · Area: analysis-common / synonyms (SynonymMap.Builder.build: the order in which the keys reach the FST; the release lines 3.4 to 10.x) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

Fixed upstream: [#16834](https://github.com/apache/lucene/pull/16834) (commit [2eb462541b1c](https://github.com/apache/lucene/commit/2eb462541b1ccc14b78bee9323beccca608e834c)), merged 2026-10-13; also on the 11.0 release branch (the same commit). On released lines only (3.4.0 to 10.6.0): main no longer had the failing code when the finding was listed.

## What it shows

You load five well-formed synonym rules (for 'a', the fullwidth letter U+FF21 and three emoji) through SolrSynonymParser with an analyzer that cuts every word to its first UTF-16 unit (WhitespaceTokenizer and TruncateTokenFilter.truncateAfterChars(1)), and you expect the fullwidth letter, which is one unit long and is never cut, to be replaced by its synonym. Instead, on Lucene 10.6.0 with assertions off it comes back unchanged and nothing throws (with assertions on, build() stops at the FST compiler's internal assertion 'inputs are added out of order'), and a second set of five rules makes build() throw ArrayIndexOutOfBoundsException with assertions off. The cut turns the three emoji into keys that are unpaired surrogates; SynonymMap.Builder.build() sorts its keys with a comparator that places such a key behind U+E000 to U+FFFF but hands the FST the keys' code points, where it belongs in front, so the FST is built from keys that are out of order (FSTCompiler.add: 'The provided input must be sorted after the previous one according to IntsRef#compareTo'; the compiler checks the order by an assertion only) and a lookup can miss a key that is stored in it (in the run the root node holds the label of U+FF21 and the lookup of it returns nothing). The same rules, in a form adapted to each release's API, fail on each of the 136 releases from 3.4.0 to 10.6.0 that were run (with the stock filter from 4.8.0 on) and pass on 10.6.0 when the filter cuts by code point, so that no pair is split, or when only the sort of build() is replaced by a comparison of code points; branch_11x and main (both unreleased) pass: there build() sorts by code point since Alan Woodward's #16834, a removal of the deprecated comparator whose text names no defect, and the paper that the comparator's own comment links (Markus W. Scherer, 'UTF-16 String Comparison in Code Point Order') notes that its fix-up 'may yield unexpected results for malformed UTF-16 strings with single, unpaired surrogate code units'.

## Expected vs actual

- Expected: You expect the fullwidth letter, which is one unit long and is never cut, to be replaced by its synonym.
- Actual: On Lucene 10.6.0 with assertions off it comes back unchanged and nothing throws (with assertions on, build() stops at the FST compiler's internal assertion 'inputs are added out of order'), and a second set of five rules makes build() throw ArrayIndexOutOfBoundsException with assertions off.

## Files

- [src/org/apache/lucene/analysis/synonym/TestSynonymRuleBesideSplitSurrogateKeys.java](src/org/apache/lucene/analysis/synonym/TestSynonymRuleBesideSplitSurrogateKeys.java) (JUnit test)

## Run it

- Tested on Lucene 10.6.0 (4ae946e5a9b9) (a commit of 2026-10-26): reproduced, with assertions on and off, on the 10.6.0 release jars, which are built from this commit (the tag releases/lucene/10.6.0).
- Tested on Lucene branch_11x 3221dd4cf3a1 (a commit of 2026-11-20): the test passes, with assertions on and off (11.0.0-SNAPSHOT): the 11.0 branch no longer has the comparator.
- This finding is in the released lines only. The trigger is a synonym-map key that holds an unpaired surrogate beside the key of a well-formed word; in this test the truncating filter makes such keys by cutting three emoji after one UTF-16 unit. The test fails with the jars of a release from 10.5.0 to 10.6.0 (TruncateTokenFilter.truncateAfterChars, which it calls, came with 10.5.0; forms of the same rules for the older APIs were run on each release from 3.4.0 on) and passes on Lucene main and on the 11.0 release branch (branch_11x), where SynonymMap.Builder.build() sorts its keys by code point (since #16834, https://github.com/apache/lucene/pull/16834). The commands below run the test first on the 10.6.0 release jars, where it fails, and then, as the control, on a build of main or of branch_11x, where it passes. For the first run put lucene-core-10.6.0.jar, lucene-codecs-10.6.0.jar, lucene-test-framework-10.6.0.jar and lucene-analysis-common-10.6.0.jar (Maven Central, org.apache.lucene) into your copy of this folder, next to the four test jars that the commands name; for another release from 10.5.0 on set V. For a run without assertions put -da -dsa -Dtests.asserts=false in place of -ea -esa. What the test does: it loads five one-word synonym rules (for 'a', the fullwidth letter U+FF21 and three emoji) through SolrSynonymParser with an analyzer of WhitespaceTokenizer and TruncateTokenFilter.truncateAfterChars(1), and expects the text U+FF21, which is one UTF-16 unit long and is never cut, to analyze to its synonym 'y'. The Gradle alternative at the end of this page was not run for this finding; a checkout of main or of branch_11x no longer has the code, so to see the failure that way the checkout has to be of a release tag from 10.5.0 to 10.6.0.
- This finding was re-run on Lucene main b5a4546a5964 on 2026-11-22: the test passed, as recorded before (a control run).
- How to read the outcome: on the jars of a release from 10.5.0 to 10.6.0 a failing test is the finding reproducing: the run reports 1 test found and 1 test failed. With assertions on, as printed, the failure is java.lang.AssertionError: inputs are added out of order lastInput=[ff21] vs input=[d83c], the FST compiler's internal assertion, thrown when the synonym map is built; with assertions off it is org.junit.ComparisonFailure: 'term 0 expected:&lt;[y]&gt; but was:&lt;[Ａ]&gt;' (Ａ is the fullwidth letter U+FF21): the letter comes back without its synonym. On a build of main or of branch_11x the same test passes in both modes (1 test successful), which is what the finding states for the unreleased lines.
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# needs JDK 25 on the PATH; run in a copy of this folder that also holds the jars named below (Maven Central)
V=10.6.0   # the release under test: any release from 10.5.0 to 10.6.0
T=randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies
T=$T:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher
CP=lucene-core-$V.jar:lucene-codecs-$V.jar:lucene-test-framework-$V.jar   # org.apache.lucene, release $V
CP=$CP:lucene-analysis-common-$V.jar:$T

javac -d classes -cp "$CP" $(find src -name '*.java')

# the finding: on the release jars the test fails (1 test failed)
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=5EED0923 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.synonym.TestSynonymRuleBesideSplitSurrogateKeys

# the control: a Lucene checkout of main or of branch_11x, built with: ./gradlew --no-daemon assemble
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT   # on branch_11x: V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar:$T

javac -d classes-unreleased -cp "$CP" $(find src -name '*.java')

# there the same test passes (1 test successful)
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=5EED0923 \
     -cp "classes-unreleased:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.synonym.TestSynonymRuleBesideSplitSurrogateKeys
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

Formal models and scripts this finding's part of the hunt used: [../../models/synonymmap-builder-key-order-unpaired-surrogate/](../../models/synonymmap-builder-key-order-unpaired-surrogate/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
