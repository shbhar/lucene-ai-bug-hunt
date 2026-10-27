# CharArrayMap.hashCode and CharArraySet.hashCode change on every call and differ for equal instances, because the inherited AbstractMap/AbstractSet hashCode sums identity hash codes of a fresh entry object and a fresh char key clone made on every iteration

Bug LFV-506 · Area: analysis / CharArraySet and CharArrayMap (stop-word, keyword and stem-override sets and maps) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a CharArrayMap with put("foo", 1) and put("bar", 2), or a CharArraySet {foo, bar} such as an analyzer's stop set, and call hashCode twice, or add it to a HashSet or HashMap and look it up again. You expect what Object.hashCode and Set/Map.hashCode require: the same value on every call to an unchanged object and equal hash codes for equal objects, so the lookup finds it. Instead every call returns a different value (for example -1124182418, then -1901051422), two equal sets hash differently, and HashSet.contains or HashMap.get with the very same instance returns false or null, because the inherited AbstractMap/AbstractSet hashCode sums the identity hash codes of a new map entry and a new char copy of each key that every iteration creates.

## Expected vs actual

- Expected: You expect what Object.hashCode and Set/Map.hashCode require: the same value on every call to an unchanged object and equal hash codes for equal objects, so the lookup finds it.
- Actual: Every call returns a different value (for example -1124182418, then -1901051422), two equal sets hash differently, and HashSet.contains or HashMap.get with the very same instance returns false or null, because the inherited AbstractMap/AbstractSet hashCode sums the identity hash codes of a new map entry and a new char copy of each key that every iteration creates.

## Files

- [src/org/apache/lucene/analysis/TestCharArrayMapHashCode.java](src/org/apache/lucene/analysis/TestCharArrayMapHashCode.java) (JUnit test)

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
     execute --select-class org.apache.lucene.analysis.TestCharArrayMapHashCode
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
