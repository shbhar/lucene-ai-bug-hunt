# AnalyzingInfixSuggester.ensureOpen publishes its new IndexWriter before the SearcherManager opened on it and does not undo that when the manager cannot be opened, so refresh refreshes the manager over build's closed writer and throws AlreadyClosedException

Bug LFV-263 · Area: suggest / analyzing infix suggester (AnalyzingInfixSuggester NRT add/update/refresh after build; inherited unchanged by BlendedInfixSuggester [read]) · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an AnalyzingInfixSuggester with the default closeIndexWriterOnBuild=true, add a suggestion and call refresh, and you expect the addition to become visible, as the add/update javadoc says, or at worst refresh to be the no-op its own comment describes. The first add after build runs ensureOpen, which assigns the writer field before it installs the SearcherManager opened on that writer, and refresh (no lock) reads writer != null as 'the current manager is live', so in that gap it refreshes build's old manager, whose writer is closed, and throws AlreadyClosedException 'this IndexWriter is closed'. The gap is transient when refresh runs on another thread during that first add (or, contested, during a rebuild), and permanent after one IOException while ensureOpen opens the manager: writer stays set, no later add repairs it, and every refresh fails until build or a reopen.

## Expected vs actual

- Expected: You expect the addition to become visible, as the add/update javadoc says, or at worst refresh to be the no-op its own comment describes.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/search/suggest/analyzing/TestAnalyzingInfixSuggesterRefreshRace.java](src/org/apache/lucene/search/suggest/analyzing/TestAnalyzingInfixSuggesterRefreshRace.java) (JUnit test)
- [src2/org/apache/lucene/search/suggest/analyzing/TestAnalyzingInfixSuggesterRefreshRace.java](src2/org/apache/lucene/search/suggest/analyzing/TestAnalyzingInfixSuggesterRefreshRace.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.analyzing.TestAnalyzingInfixSuggesterRefreshRace
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.analyzing.TestAnalyzingInfixSuggesterRefreshRace
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/suggesters/](../../models/suggesters/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
