# AnalyzingInfixSuggester.ensureOpen publishes its new IndexWriter before the SearcherManager opened on it and does not undo that when the manager cannot be opened, so refresh refreshes the manager over build's closed writer and throws AlreadyClosedException

Bug LFV-263 · Area: suggest / analyzing infix suggester (AnalyzingInfixSuggester NRT add/update/refresh after build; inherited unchanged by BlendedInfixSuggester [read]) · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an AnalyzingInfixSuggester with the default closeIndexWriterOnBuild=true, add a suggestion and call refresh, and you expect the addition to become visible, as the add/update javadoc says, or at worst refresh to be the no-op its own comment describes. The first add after build runs ensureOpen, which assigns the writer field before it installs the SearcherManager opened on that writer, and refresh (no lock) reads writer != null as 'the current manager is live', so in that gap it refreshes build's old manager, whose writer is closed, and throws AlreadyClosedException 'this IndexWriter is closed'. The gap is transient when refresh runs on another thread during that first add (or, contested, during a rebuild), and permanent after one IOException while ensureOpen opens the manager: writer stays set, no later add repairs it, and every refresh fails until build or a reopen.

The test shows that after a default build() (closeIndexWriterOnBuild=true), one I/O error while the first add()'s ensureOpen() opens its SearcherManager leaves `writer` set beside build()'s stale manager, so after a successful retried add() every refresh() refreshes the closed writer.

## Expected vs actual

Expected: refresh() makes the added suggestion visible (lookup "penn" returns 1); actual: `AlreadyClosedException: this IndexWriter is closed` from SearcherManager.refreshIfNeeded inside AnalyzingInfixSuggester.refresh().

## Files

- [src/org/apache/lucene/search/suggest/analyzing/TestInfixSuggesterEnsureOpenFailure.java](src/org/apache/lucene/search/suggest/analyzing/TestInfixSuggesterEnsureOpenFailure.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
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
     execute --select-class org.apache.lucene.search.suggest.analyzing.TestInfixSuggesterEnsureOpenFailure
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/suggesters/](../../models/suggesters/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
