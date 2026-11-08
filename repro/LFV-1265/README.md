# IntObjectHashMap, LongObjectHashMap and CharObjectHashMap clear() reset the keys but never null the values array, so every former value stays strongly reachable; the default codec's Lucene103BlockTreeTermsReader.close() and Lucene90PointsReader.close(), which call clear() so that a closed reader's per-field objects can be garbage collected, therefore release nothing (since 9.11.0)

Finding LFV-1265 · Area: core / util (internal hppc object maps: clear(); terms and points readers' close()) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1265.html](../../bugs/LFV-1265.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You put a value into an IntObjectHashMap (or a LongObjectHashMap or CharObjectHashMap) and call clear(). You expect the map to drop its reference to the value, as java.util.HashMap.clear() and the HPPC code these classes were forked from do, and as the default codec's Lucene103BlockTreeTermsReader.close() ('Clear so refs to terms index is GCable even if app hangs onto us') and Lucene90PointsReader.close() ('Free up heap') rely on. Instead clear() resets only the keys (where HPPC fills the values with null there is an empty '/* */' comment), so size() is 0 but the values array still holds the object, and an application that keeps a closed DirectoryReader keeps every field's FieldReader and BKDReader reachable.

## Expected vs actual

- Expected: After `clear()` every slot of `values` is null: the map holds no reference to the old value.
- Actual: `java.lang.AssertionError: expected null, but was:<java.lang.Object@1f53ff81>` at `TestIntObjectHashMapClear.java:29`: the value is still in the array.

## Files

- [src/org/apache/lucene/internal/hppc/TestIntObjectHashMapClear.java](src/org/apache/lucene/internal/hppc/TestIntObjectHashMapClear.java) (JUnit test)

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
java -ea -esa -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=3 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.internal.hppc.TestIntObjectHashMapClear
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestIntObjectHashMapClear.java](TestIntObjectHashMapClear.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
