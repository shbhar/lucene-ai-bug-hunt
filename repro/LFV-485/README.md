# CharArraySet.unmodifiableSet / CharArrayMap.unmodifiableMap share the backing table but copy its size, so after the backing set grows the view reports a stale size, sees only some additions, and can loop forever on an absent key

Bug LFV-485 · Area: analysis / CharArraySet and CharArrayMap unmodifiable views (stop-word, keyword and protected-word sets) · Priority: low · Impact: hang · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You create a small CharArraySet (new CharArraySet(0, true)), add "the", hand out CharArraySet.unmodifiableSet(set) as a read-only view, as its javadoc suggests, and keep adding stop words to the original. You expect the view to be a consistent set, either a live view whose size and contents follow the original or a snapshot, and in any case a contains that returns. Instead the view shares the original's hash-table arrays but copied its size, so after one more add it reports size 1 while it iterates 2 words, after the original rehashes it sees only some of the later words, and once the original's 8-slot table takes its 8th word the view keeps that full table and contains of any word not in the set (and a StopFilter or StandardAnalyzer over the view analysing "lucene") never returns.

## Expected vs actual

- Expected: You expect the view to be a consistent set, either a live view whose size and contents follow the original or a snapshot, and in any case a contains that returns.
- Actual: The view shares the original's hash-table arrays but copied its size, so after one more add it reports size 1 while it iterates 2 words, after the original rehashes it sees only some of the later words, and once the original's 8-slot table takes its 8th word the view keeps that full table and contains of any word not in the set (and a StopFilter or StandardAnalyzer over the view analysing "lucene") never returns.

## Files

- [src/org/apache/lucene/analysis/TestCharArraySetUnmodifiableView.java](src/org/apache/lucene/analysis/TestCharArraySetUnmodifiableView.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.TestCharArraySetUnmodifiableView
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
