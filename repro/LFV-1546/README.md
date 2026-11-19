# MinHashFilterFactory, DropIfFlaggedFilterFactory, FixedShingleFilterFactory and PatternCaptureGroupFilterFactory accept a parameter they do not read instead of refusing it with IllegalArgumentException (the rule of LUCENE-4877 since 4.3.0), so with the other arguments valid a misspelt optional key, such as preserveOriginal for preserve_original, is ignored without an error (Lucene's own TestDropIfFlaggedFilterFactory passes such a key); run on 10.6.0, branch_11x, main and each factory's first release (4.4.0, 6.2.0, 7.4.0, 8.9.0); known outside Lucene

Finding LFV-1546 · Area: analysis-common / token filter factories (the leftover-argument check of the Map constructor: MinHashFilterFactory, DropIfFlaggedFilterFactory, FixedShingleFilterFactory, PatternCaptureGroupFilterFactory) · Priority: low · Impact: minor · Novelty: known outside Lucene

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure one of four token filter factories (minHash, dropIfFlagged, fixedShingle, patternCaptureGroup) and misspell an optional parameter, for example preserveOriginal=false where PatternCaptureGroupFilterFactory reads preserve_original. You expect an IllegalArgumentException that names the unknown parameter: that is the rule Lucene gave its analysis factories in 4.3.0 (LUCENE-4877, listed under Bug Fixes; no javadoc states it), and the sibling factories run here (patternReplace, shingle, lowercase, apostrophe, asciiFolding, edgeNGram) throw it. Instead, with their other arguments valid, these four factories are built and ignore the key, so the filter runs with its default and nothing says so (in the example 'abc123' gives [abc123, 123] where preserve_original=false gives [123]); Lucene's own TestDropIfFlaggedFilterFactory passes the key flags to a factory that reads dropFlags, and the missing check hides it; not new in 11: each of the four has done this since the release that first shipped it (4.4.0, 6.2.0, 7.4.0, 8.9.0).

## Expected vs actual

- Expected: You expect an IllegalArgumentException that names the unknown parameter: that is the rule Lucene gave its analysis factories in 4.3.0 (LUCENE-4877, listed under Bug Fixes; no javadoc states it), and the sibling factories run here (patternReplace, shingle, lowercase, apostrophe, asciiFolding, edgeNGram) throw it.
- Actual: With their other arguments valid, these four factories are built and ignore the key, so the filter runs with its default and nothing says so (in the example 'abc123' gives [abc123, 123] where preserve_original=false gives [123]); Lucene's own TestDropIfFlaggedFilterFactory passes the key flags to a factory that reads dropFlags, and the missing check hides it; not new in 11: each of the four has done this since the release that first shipped it (4.4.0, 6.2.0, 7.4.0, 8.9.0).

## Files

- [src/org/apache/lucene/analysis/pattern/TestPatternCaptureGroupFilterFactory.java](src/org/apache/lucene/analysis/pattern/TestPatternCaptureGroupFilterFactory.java) (JUnit test)

## Run it

- Tested on Lucene main 1ae70c6e45b8 (a commit of 2026-11-19): still reproduces.
- Tested on Lucene main ec3b3cca8f10 (a commit of 2026-11-18): reproduced.
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

# run 1: assertions on, seed 0166873A
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=0166873A \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.pattern.TestPatternCaptureGroupFilterFactory
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

Formal models and scripts this finding's part of the hunt used: [../../models/analysis-factories-unread-parameters/](../../models/analysis-factories-unread-parameters/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
