# AnalyzingSuggester and FuzzySuggester count skipped duplicate copies of a surface form toward maxSurfaceFormsPerAnalyzedForm, so distinct lower-weighted surface forms are silently dropped

Bug LFV-248 · Area: suggest / analyzing suggester (AnalyzingSuggester.build, inherited by FuzzySuggester) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-248.html](../../bugs/LFV-248.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an AnalyzingSuggester or FuzzySuggester from entries in which one text repeats, for example 256 documents with 'Apple iPhone' and one with 'apple iphone', which a lowercasing analyzer maps to the same analyzed form, and you expect both surface forms back, because maxSurfaceFormsPerAnalyzedForm (default 256) is documented as the 'maximum number of surface forms to keep for a single analyzed form' and repeated copies are documented as removed. build increments its per-analyzed-form counter and applies the limit before it checks whether the surface form was already seen, so every skipped copy still uses up a slot: lookup('apple ip') returns only [Apple iPhone], and with a limit of 2 the entries a/10, a/5, A/3 give [a/10] instead of [a/10, A/3]. With 255 copies, or without the repeated copy, both forms are returned.

## Expected vs actual

- Expected: `[a/10, A/3]` for the limit-2 case; `[Apple iPhone/355, apple iphone/5]` for 256 documents (both suggesters); `apple iphone/5` first when typed exactly.
- Actual: `[a/10]`, and `[Apple iPhone/355]` in all three 256-document cases; with 255 documents both forms come back.

## Files

- [src/org/apache/lucene/search/suggest/analyzing/TestAnalyzingSuggesterDuplicateSurfaceForms.java](src/org/apache/lucene/search/suggest/analyzing/TestAnalyzingSuggesterDuplicateSurfaceForms.java) (JUnit test)
- [src2/org/apache/lucene/search/suggest/analyzing/TestAnalyzingSuggesterDuplicateSurfaceForms.java](src2/org/apache/lucene/search/suggest/analyzing/TestAnalyzingSuggesterDuplicateSurfaceForms.java) (JUnit test)
- [src3/org/apache/lucene/search/suggest/analyzing/TestAnalyzingSuggesterDuplicateSurfaceForms.java](src3/org/apache/lucene/search/suggest/analyzing/TestAnalyzingSuggesterDuplicateSurfaceForms.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
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

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')
javac -d classes-src3 -cp "$CP" $(find src3 -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.analyzing.TestAnalyzingSuggesterDuplicateSurfaceForms
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.analyzing.TestAnalyzingSuggesterDuplicateSurfaceForms
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src3:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.analyzing.TestAnalyzingSuggesterDuplicateSurfaceForms
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestAnalyzingSuggesterRepeatedSurfaceForms.java](TestAnalyzingSuggesterRepeatedSurfaceForms.java)
- [TestAnalyzingSuggesterSurfaceFormLimit.java](TestAnalyzingSuggesterSurfaceFormLimit.java)
- [TestDedupInputWorkaround.java](TestDedupInputWorkaround.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
