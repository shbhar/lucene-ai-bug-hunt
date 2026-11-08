# SortField.equals is not symmetric with ToParentBlockJoinSortField: new SortField("price", INT).equals(a block-join sort field on the same field and type) is true, the reverse is false, and the two hash codes differ, so Sort.equals gives a different answer depending on which sort is the receiver (every release since 6.6.0)

Finding LFV-1294 · Area: core / search: SortField equals and hashCode (with join's ToParentBlockJoinSortField) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1294.html](../../bugs/LFV-1294.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You create a plain sort field, new SortField("price", SortField.Type.INT), and a block-join sort field on the same field and type, new ToParentBlockJoinSortField("price", SortField.Type.INT, false, parents, children), and compare them. You expect equals to be symmetric and equal objects to have equal hash codes, as java.lang.Object requires of the equals and hashCode that SortField overrides, and as SortField.equals' own comment on main says for its newest subclass ('NonPruningSortField is never equal to a plain SortField, which keeps equals symmetric'). Instead plain.equals(join) is true while join.equals(plain) is false and the hash codes differ, because SortField.equals accepts any subclass via instanceof and compares only its five base fields, while ToParentBlockJoinSortField.equals adds a getClass() check and its hashCode also covers its filters; new Sort(plain).equals(new Sort(join)) inherits the same one-way answer.

## Expected vs actual

- Expected: `plain.equals(join)` and `join.equals(plain)` give the same answer (both `false`: the two sorts order parents differently).
- Actual: `plain.equals(join)` is `true`, `join.equals(plain)` is `false`: `AssertionError: expected:<true> but was:<false>`, assertions on and off.

## Files

- [src/org/apache/lucene/search/join/TestSortFieldEqualsSymmetry.java](src/org/apache/lucene/search/join/TestSortFieldEqualsSymmetry.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/join/build/libs/lucene-join-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.join.TestSortFieldEqualsSymmetry
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestSortFieldEqualsSymmetry.java](TestSortFieldEqualsSymmetry.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
