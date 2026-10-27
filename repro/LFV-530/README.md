# HighFrequencyDictionary javadoc says terms appearing in less than thresh of the documents are excluded, but the code rounds thresh * numDocs down and keeps them (a term in 1 of 3 documents passes thresh 0.5); documentation/contract defect, the javadoc fix is the default proposal

Bug LFV-530 · Area: suggest / spell (HighFrequencyDictionary threshold) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a HighFrequencyDictionary over 3 documents with thresh 0.5 and, because its javadoc says 'Terms appearing in less than thresh percentage of documents will be excluded', you expect a term that occurs in 1 of the 3 documents (33%) to be left out. Instead it is returned: the cut-off is computed as (int) (0.5f * 3) = 1, a floor, so every term in floor(thresh * numDocs) documents passes even when that is below thresh. The floor has been the behaviour since 2007 and a sibling class and a downstream test rely on it, so the javadoc is the side to fix by default; an exact-ceiling code change is the alternative.

## Expected vs actual

- Expected: You expect a term that occurs in 1 of the 3 documents (33%) to be left out.
- Actual: It is returned: the cut-off is computed as (int) (0.5f * 3) = 1, a floor, so every term in floor(thresh * numDocs) documents passes even when that is below thresh.

## Files

- [src/org/apache/lucene/search/spell/TestHighFrequencyDictionaryThresholdRounding.java](src/org/apache/lucene/search/spell/TestHighFrequencyDictionaryThresholdRounding.java) (JUnit test)

## Run it

- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/suggest/build/libs/lucene-suggest-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.spell.TestHighFrequencyDictionaryThresholdRounding
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
