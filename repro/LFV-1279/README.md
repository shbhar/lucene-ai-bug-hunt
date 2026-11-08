# CharArraySet.copy(map.keySet()) keeps the CharArrayMap's values, so add(key) on the copy returns true for a key that is already present but was mapped to null

Finding LFV-1279 · Area: analysis / CharArraySet and CharArrayMap (copy of a key-set view) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1279.html](../../bugs/LFV-1279.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You put a key with a null value into a CharArrayMap (map.put("x", null)), make a copy of its key set with CharArraySet.copy(map.keySet()), and call add("x") on the copy. You expect false, because the copy already contains "x" (contains returns true) and java.util.Set.add returns false for an element that is already present. Instead add returns true (and addAll reports the set as changed), because copy clones the map's values along with its keys and CharArraySet.add treats a null old value as 'not there before'; the size stays 1.

## Expected vs actual

- Expected: `copy.add("x")` returns `false`, because the copy already contains "x", and the test passes.
- Actual: `copy.add("x")` returns `true`: `AssertionError` at line 30, after `contains("x")` passed, with assertions on and off.

## Files

- [src/org/apache/lucene/analysis/TestCharArraySetCopyOfKeySet.java](src/org/apache/lucene/analysis/TestCharArraySetCopyOfKeySet.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.TestCharArraySetCopyOfKeySet
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestCharArraySetCopyOfKeySet.java](TestCharArraySetCopyOfKeySet.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
