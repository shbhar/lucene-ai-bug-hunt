# RegExp builds about n^2/2 transitions for a run of n repeated nullable atoms (a.*.*...b, a(x?)^n b, [a-z]*, (bc)?), and on main RegexpQuery keeps that automaton: a 2,002-character regexp of language a.*b holds 502,502 transitions and 170 MB (10.5.2: 4 states, 7 KB)

Bug LFV-962 · Area: util / automaton (RegExp, Operations.concatenate; reached from RegexpQuery and the classic and flexible query parsers) · Priority: unranked · Impact: crash · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a RegexpQuery, directly or by typing /regexp/ syntax into the classic or flexible query parser, for a regexp with a long run of the same nullable atom, for example 'a' followed by 1,000 copies of '.*' and then 'b', and expect an automaton about as small as its language a.*b (4 states, which is what 10.5.2 keeps and what WildcardQuery builds for a run of '*' since #16382). Instead Operations.concatenate gives every accept state a link to each following piece for as long as those pieces accept the empty string, so the run gets about n^2/2 transitions (502,502 here), and on main, where RegexpQuery no longer determinizes (#15939), the query keeps that automaton: ramBytesUsed 170 MB, about 3 s to build, and OutOfMemoryError under a 1 GB heap from about 4,000 characters. The same happens for x?, x*, [a-z]*, (ab)*, (bc)? and [^x]*; search results are always correct, and released versions pay the quadratic cost only while building (OutOfMemoryError at about 12,000 characters under 1 GB, or a TooComplexToDeterminizeException for 447 'x?' in a row).

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: Operations.concatenate gives every accept state a link to each following piece for as long as those pieces accept the empty string, so the run gets about n^2/2 transitions (502,502 here), and on main, where RegexpQuery no longer determinizes (#15939), the query keeps that automaton: ramBytesUsed 170 MB, about 3 s to build, and OutOfMemoryError under a 1 GB heap from about 4,000 characters.

## Files

- [src/org/apache/lucene/search/TestRegexpNullableRunSize.java](src/org/apache/lucene/search/TestRegexpNullableRunSize.java) (JUnit test)
- [src/org/apache/lucene/queryparser/TestRegexpNullableRunParsers.java](src/org/apache/lucene/queryparser/TestRegexpNullableRunParsers.java) (JUnit test)
- [src/org/apache/lucene/util/automaton/TestConcatenateNullableChain.java](src/org/apache/lucene/util/automaton/TestConcatenateNullableChain.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:$LUCENE/lucene/queryparser/build/libs/lucene-queryparser-$V.jar
CP=$CP:$LUCENE/lucene/sandbox/build/libs/lucene-sandbox-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx2g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=4 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestRegexpNullableRunSize \
       --select-class org.apache.lucene.queryparser.TestRegexpNullableRunParsers \
       --select-class org.apache.lucene.util.automaton.TestConcatenateNullableChain
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
