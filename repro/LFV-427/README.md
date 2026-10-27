# MutableValueFloat and MutableValueDouble equals compare with ==, so a NaN value is not equal to itself and grouping by a float or double function puts NaN documents into empty groups (released versions) or throws IllegalArgumentException (main)

Bug LFV-427 · Area: grouping / function grouping (core util.mutable: MutableValueFloat and MutableValueDouble equality) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You group search hits by a float or double function, for example new DoubleFieldSource("v") or a 0/0 division, and some documents have the value NaN; you expect them to form one NaN group, because MutableValueDouble's own hashCode (doubleToLongBits) and compareTo (Double.compare) treat every NaN as one value. Instead equalsSameType compares with ==, under which NaN is not even equal to itself, so on released versions every NaN document becomes its own group, each such group comes back with 0 documents and the NaN documents drop out of the grouped hit count. On main the same search fails with IllegalArgumentException 'group values differ across shards', because the result merge compares the NaN group value with itself.

## Expected vs actual

- Expected: You expect them to form one NaN group, because MutableValueDouble's own hashCode (doubleToLongBits) and compareTo (Double.compare) treat every NaN as one value.
- Actual: equalsSameType compares with ==, under which NaN is not even equal to itself, so on released versions every NaN document becomes its own group, each such group comes back with 0 documents and the NaN documents drop out of the grouped hit count.

## Files

- [src/org/apache/lucene/search/grouping/TestMutableValueFloatingPointEquals.java](src/org/apache/lucene/search/grouping/TestMutableValueFloatingPointEquals.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/grouping/build/libs/lucene-grouping-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.grouping.TestMutableValueFloatingPointEquals
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/twin-family-static-diff/](../../models/twin-family-static-diff/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
