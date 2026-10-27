--------------------------- MODULE MC_TaskExec ---------------------------
EXTENDS TaskExec
Flat2 == <<2>>
Flat3 == <<3>>
NoChild2 == << <<0, 0>> >>
NoChild3 == << <<0, 0, 0>> >>
\* invocation 1 has 2 tasks; each of them calls invokeAll with 2 tasks (invocations 2 and 3)
Nest == <<2, 2, 2>>
NestChild == << <<2, 3>>, <<0, 0>>, <<0, 0>> >>
NoThrow == {}
Throw2of1 == {<<1, 2>>}
AllThrowFlat3 == {<<1, 1>>, <<1, 2>>, <<1, 3>>}
NestThrow == {<<1, 1>>, <<2, 1>>, <<3, 2>>}
\* invocation 1 has 3 tasks, each calls invokeAll with 2 tasks (invocations 2..4)
Nest3 == <<3, 2, 2, 2>>
Nest3Child == << <<2, 3, 4>>, <<0, 0>>, <<0, 0>>, <<0, 0>> >>
Nest3Throw == {<<1, 1>>, <<2, 2>>, <<4, 1>>}
\* depth 3: 1 -> 2 -> 3
Deep == <<2, 2, 2>>
DeepChild == << <<2, 0>>, <<3, 0>>, <<0, 0>> >>
DeepThrow == {<<3, 2>>, <<1, 2>>}
=============================================================================
