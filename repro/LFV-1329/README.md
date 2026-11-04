# CharArrayMap.entrySet().contains returns true for an entry whose value is null and whose key is not in the map, even on an empty map, and entrySet().containsAll and entrySet().equals inherit the wrong answer (since 3.1.0)

Finding LFV-1329 · Area: analysis / CharArrayMap entry-set view (EntrySet.contains) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1329.html](../../bugs/LFV-1329.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You create an empty CharArrayMap (new CharArrayMap&lt;&gt;(0, false)) and ask its entry set whether it contains the entry ("missing", null). You expect false, because the map has no mappings (entrySet().size() is 0), Map.entrySet() is a set view of the mappings the map contains, and java.util.HashMap answers false. Instead contains returns true, because EntrySet.contains treats get(key) == null as a match for a null value without checking that the key is present; the same happens for any absent key on a non-empty map, and entrySet().containsAll and entrySet().equals inherit the wrong answer.

## Expected vs actual

- Expected: `entrySet().contains(("missing", null))` on the empty map returns `false`, and the test passes.
- Actual: It returns `true`: `AssertionError` at line 26 (the `assertFalse`), with assertions on and off.

## Files

- [src/org/apache/lucene/analysis/TestCharArrayMapEntrySetContains.java](src/org/apache/lucene/analysis/TestCharArrayMapEntrySetContains.java) (JUnit test)

## Run it

- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.TestCharArrayMapEntrySetContains
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestCharArrayMapEntrySetContains.java](TestCharArrayMapEntrySetContains.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
