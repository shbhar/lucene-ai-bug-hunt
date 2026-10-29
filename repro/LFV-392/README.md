# BytesRefArray.iterator(Comparator) throws NullPointerException for a null comparator once the array holds 2 or more values, instead of iterating in append order as its javadoc says (regression since 8.5.0, LUCENE-9228)

Bug LFV-392 · Area: util / BytesRefArray (iterator(Comparator) with a null comparator) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You append two or more values (for example c, a, b) to a BytesRefArray and call iterator((Comparator&lt;BytesRef&gt;) null), expecting the values back in append order, as the method's javadoc promises ('Otherwise the order is the same as the values were appended') and as Lucene 8.4.1 and earlier do. Instead the call throws NullPointerException ('Cannot invoke Comparator.compare... because this.val$cmp is null', from StringSorter$1.compare via BytesRefArray.sort), because the method now always sorts with the given comparator; with 0 or 1 values, or with the no-arg iterator, append order still comes back. The one-line fix restores the pre-8.5 null check ('return iterator(comp == null ? null: sort(comp, false));'); a maintainer may instead prefer to correct the javadoc.

The test shows that `BytesRefArray.iterator((Comparator<BytesRef>) null)` on an array holding two values (b, a) throws NullPointerException instead of iterating in append order, because `iterator(Comparator)` always sorts with the given comparator.

## Expected vs actual

Expected: b, then a (javadoc: "Otherwise the order is the same as the values were appended"). Actual on main with -ea and with -da: NullPointerException ("this.val$cmp" is null) at StringSorter$1.compare (StringSorter.java:102).

## Files

- [src/org/apache/lucene/util/TestBytesRefArrayIteratorNullComparator.java](src/org/apache/lucene/util/TestBytesRefArrayIteratorNullComparator.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.TestBytesRefArrayIteratorNullComparator
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
