# BytesRefHash accepts initial capacity 1 but never grows the table, so add or find of any second distinct key spins forever; since 10.3.0 the same capacity also stores a key whose hashcode is -1 as an empty slot

Bug LFV-422 · Area: util / BytesRefHash (initial capacity 1: rehash trigger, linear probe and slot word) · Priority: low · Impact: hang · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You create new BytesRefHash(pool, 1, new BytesRefHash.DirectBytesStartArray(1)), which the constructor accepts because it rejects only capacities &lt;= 0 and non-powers of two, add the key 'a', then add or look up 'b', expecting id 1 or -1 at once, as with capacity 2. Instead the call never returns: with one slot, hashHalfSize is 0, so add's rehash trigger 'count == hashHalfSize' never fires, the single slot stays full, and the linear probe in findHash loops forever looking for an empty slot. Since 10.3.0 (#14720) the same capacity also stores a key whose seeded murmur3 hashcode is exactly -1 as the EMPTY marker, so find returns -1 for a key that was just added and adding it again gives it a second id.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The call never returns: with one slot, hashHalfSize is 0, so add's rehash trigger 'count == hashHalfSize' never fires, the single slot stays full, and the linear probe in findHash loops forever looking for an empty slot.

## Files

- [src/org/apache/lucene/util/TestBytesRefHashCapacityOne.java](src/org/apache/lucene/util/TestBytesRefHashCapacityOne.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.TestBytesRefHashCapacityOne
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
