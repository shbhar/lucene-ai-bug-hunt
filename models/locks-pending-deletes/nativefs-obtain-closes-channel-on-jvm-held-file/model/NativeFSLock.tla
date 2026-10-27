------------------------------ MODULE NativeFSLock ------------------------------
(***************************************************************************)
(* TLA+ model of org.apache.lucene.store.NativeFSLockFactory on Lucene main *)
(* @ ea57360912ed (after #16278 / #16303), together with the two layers it  *)
(* relies on:                                                              *)
(*   - the JDK FileChannel lock table (sun.nio.ch.FileLockTable): JVM-wide, *)
(*     keyed by file identity (device + inode); tryLock throws             *)
(*     OverlappingFileLockException when ANY channel of this JVM holds a   *)
(*     valid lock on the same file; closing a channel invalidates only the *)
(*     FileLock objects acquired through that channel;                     *)
(*   - POSIX fcntl record locks (what the JDK uses on Linux): owned by the *)
(*     process, keyed by inode; closing ANY descriptor of the process on   *)
(*     that inode releases ALL of the process's locks on it (documented in *)
(*     java.nio.channels.FileLock "Platform dependencies").                *)
(*                                                                         *)
(* Mapping model element -> Java line (NativeFSLockFactory.java @ main):    *)
(*   CreateFile      L84     Files.createFile(lockFile) (EEXIST ignored)    *)
(*   RealPath        L94     lockFile.toRealPath() (fails if no file)       *)
(*   Attr            L105-6  readAttributes(realPath).creationTime()        *)
(*   Add             L108    LOCK_HELD.add(realPath.toString()) / L119 LOFE *)
(*   Open            L132    FileChannel.open(path, CREATE, WRITE)          *)
(*   TryLock         L133-8  channel.tryLock(); null -> LOFE (L137)         *)
(*   FailClose       L139-41 catch Throwable: closeWhileSuppressing(channel)*)
(*   Clear           L113-16 finally: clearLockHeld (L145-150)              *)
(*   CloseRel        L206-7  try-with-resources closes FileLock (release)   *)
(*   CloseCh         L206    ... then the FileChannel                       *)
(*   CloseFin        L210-12 finally: closed = true; clearLockHeld          *)
(*   Valid(k)        L170-97 ensureValid: closed, LOCK_HELD.contains,       *)
(*                           lock.isValid, channel.size, creationTime       *)
(*   Interrupt       FileChannel is an InterruptibleChannel: an interrupt   *)
(*                   during channel I/O (e.g. channel.size() in ensureValid) *)
(*                   closes it asynchronously                               *)
(*   ExtDelete/ExtRecreate  another program or a user deletes / re-creates  *)
(*                   write.lock (testDeleteLockFile scenario)               *)
(*                                                                         *)
(* Paths are canonical strings (toRealPath already resolved symlinks).     *)
(* Aliases that toRealPath cannot resolve are modelled by DirOf / InitEntry *)
(* (bind mount: two path strings, one directory entry; hard link: two      *)
(* entries, one inode).  Class loaders: LoaderOf (each loader has its own  *)
(* static LOCK_HELD).                                                      *)
(***************************************************************************)
EXTENDS Naturals, FiniteSets, TLC

CONSTANTS
  Threads, Procs, Loaders, Paths, Dirs,
  ProcOf,       \* [Threads -> Procs]
  LoaderOf,     \* [Threads -> Loaders] (a loader belongs to one process)
  PathOf,       \* [Threads -> Paths]    canonical lock-file path string used by the thread
  DirOf,        \* [Paths -> Dirs]       which directory entry a path string names
  InitEntry,    \* [Dirs -> 0..MaxInode] initial inode of each entry (0 = no file)
  MaxInode,     \* inode budget
  MaxAttempts,  \* obtain calls per thread
  MaxExternal,  \* external delete / re-create budget
  Faults,       \* inject IOExceptions at every I/O step of obtain
  CtimeCollide, \* creation times are not unique (coarse timestamps)
  Interrupts,   \* allow async channel close by Thread.interrupt
  UseLockHeld   \* FALSE = mutant without the LOCK_HELD set (pre LUCENE-5738)

NONE == "none"
MaxLocks == Cardinality(Threads) * MaxAttempts
LockIds == 1..MaxLocks
Inodes == 1..MaxInode

VARIABLES
  entry,      \* [Dirs -> 0..MaxInode]
  nextInode,  \* next fresh inode number
  osLock,     \* [Inodes -> Procs \cup {NONE}]  fcntl owner
  lockHeld,   \* [Loaders -> SUBSET Paths]      NativeFSLockFactory.LOCK_HELD per loader
  locks,      \* [LockIds -> record] NativeFSLock objects (alive = TRUE once created)
  nLocks,
  pc,         \* [Threads -> state]
  tl,         \* thread locals: ctime read at L106, open channel inode, lock being closed
  attempts,
  ext

vars == <<entry, nextInode, osLock, lockHeld, locks, nLocks, pc, tl, attempts, ext>>

Ctime(i) == IF CtimeCollide THEN 1 ELSE i

NoLock == [alive |-> FALSE, owner |-> CHOOSE t \in Threads : TRUE, proc |-> CHOOSE p \in Procs : TRUE,
           loader |-> CHOOSE l \in Loaders : TRUE, path |-> CHOOSE s \in Paths : TRUE,
           inode |-> 0, ctime |-> 0, closed |-> TRUE, chanOpen |-> FALSE, jdkValid |-> FALSE]

Init ==
  /\ entry = InitEntry
  /\ nextInode = 1 + (LET S == {InitEntry[d] : d \in Dirs} IN
                        CHOOSE m \in S \cup {0} : \A x \in S : x <= m)
  /\ osLock = [i \in Inodes |-> NONE]
  /\ lockHeld = [l \in Loaders |-> {}]
  /\ locks = [k \in LockIds |-> NoLock]
  /\ nLocks = 0
  /\ pc = [t \in Threads |-> "idle"]
  /\ tl = [t \in Threads |-> [ctime |-> 0, chan |-> 0, k |-> 0]]
  /\ attempts = [t \in Threads |-> 0]
  /\ ext = 0

Live(k) == k \in 1..nLocks /\ locks[k].alive
\* the JDK lock table of process p holds a valid FileLock on inode i
JdkHeld(p, i) == \E k \in 1..nLocks : Live(k) /\ locks[k].proc = p /\ locks[k].inode = i /\ locks[k].jdkValid
\* POSIX: process p closes one descriptor on inode i -> all of p's locks on i go away
ReleaseOnClose(p, i) == [osLock EXCEPT ![i] = IF @ = p THEN NONE ELSE @]

Dir(t) == DirOf[PathOf[t]]
L(t) == LoaderOf[t]
P(t) == ProcOf[t]
S(t) == PathOf[t]

Goto(t, s) == pc' = [pc EXCEPT ![t] = s]

StartObtain(t) ==
  /\ pc[t] = "idle" /\ attempts[t] < MaxAttempts
  /\ attempts' = [attempts EXCEPT ![t] = @ + 1]
  /\ Goto(t, "create")
  /\ UNCHANGED <<entry, nextInode, osLock, lockHeld, locks, nLocks, tl, ext>>

\* L84: Files.createFile -- open(O_CREAT|O_EXCL): EEXIST opens no descriptor
CreateFile(t) ==
  /\ pc[t] = "create"
  /\ \/ /\ entry[Dir(t)] = 0 /\ nextInode <= MaxInode
        /\ entry' = [entry EXCEPT ![Dir(t)] = nextInode]
        /\ nextInode' = nextInode + 1
     \/ /\ (entry[Dir(t)] # 0 \/ Faults)
        /\ UNCHANGED <<entry, nextInode>>       \* exists, or creation failed (ignored)
  /\ Goto(t, "realpath")
  /\ UNCHANGED <<osLock, lockHeld, locks, nLocks, tl, attempts, ext>>

\* L94: toRealPath fails if the file does not exist (or I/O fault)
RealPath(t) ==
  /\ pc[t] = "realpath"
  /\ IF entry[Dir(t)] = 0 THEN Goto(t, "idle")
     ELSE \/ Goto(t, "attr")
          \/ (Faults /\ Goto(t, "idle"))
  /\ UNCHANGED <<entry, nextInode, osLock, lockHeld, locks, nLocks, tl, attempts, ext>>

\* L105-106: creation time of whatever file the path names now
Attr(t) ==
  /\ pc[t] = "attr"
  /\ IF entry[Dir(t)] = 0
       THEN /\ Goto(t, "idle") /\ UNCHANGED tl
       ELSE \/ /\ tl' = [tl EXCEPT ![t].ctime = Ctime(entry[Dir(t)])]
               /\ Goto(t, "add")
            \/ (Faults /\ Goto(t, "idle") /\ UNCHANGED tl)
  /\ UNCHANGED <<entry, nextInode, osLock, lockHeld, locks, nLocks, attempts, ext>>

\* L108 / L119
Add(t) ==
  /\ pc[t] = "add"
  /\ IF ~UseLockHeld
       THEN Goto(t, "open") /\ UNCHANGED lockHeld
       ELSE IF S(t) \notin lockHeld[L(t)]
              THEN /\ lockHeld' = [lockHeld EXCEPT ![L(t)] = @ \cup {S(t)}]
                   /\ Goto(t, "open")
              ELSE Goto(t, "idle") /\ UNCHANGED lockHeld       \* LockObtainFailedException
  /\ UNCHANGED <<entry, nextInode, osLock, locks, nLocks, tl, attempts, ext>>

\* L132: FileChannel.open(path, CREATE, WRITE): opens (or creates) whatever the path names now
Open(t) ==
  /\ pc[t] = "open"
  /\ \/ /\ entry[Dir(t)] # 0
        /\ tl' = [tl EXCEPT ![t].chan = entry[Dir(t)]]
        /\ Goto(t, "trylock") /\ UNCHANGED <<entry, nextInode>>
     \/ /\ entry[Dir(t)] = 0 /\ nextInode <= MaxInode
        /\ entry' = [entry EXCEPT ![Dir(t)] = nextInode]
        /\ nextInode' = nextInode + 1
        /\ tl' = [tl EXCEPT ![t].chan = nextInode]
        /\ Goto(t, "trylock")
     \/ /\ (Faults \/ (entry[Dir(t)] = 0 /\ nextInode > MaxInode))
        /\ Goto(t, "clear") /\ UNCHANGED <<entry, nextInode, tl>>   \* open threw; channel == null
  /\ UNCHANGED <<osLock, lockHeld, locks, nLocks, attempts, ext>>

\* L133-138
TryLock(t) ==
  LET i == tl[t].chan IN
  /\ pc[t] = "trylock"
  /\ IF JdkHeld(P(t), i)
       THEN /\ Goto(t, "failclose")                       \* OverlappingFileLockException
            /\ UNCHANGED <<osLock, locks, nLocks>>
     ELSE IF osLock[i] \notin {NONE, P(t)}
       THEN /\ Goto(t, "failclose")                       \* tryLock() == null -> LOFE (L137)
            /\ UNCHANGED <<osLock, locks, nLocks>>
     ELSE \/ /\ osLock' = [osLock EXCEPT ![i] = P(t)]
             /\ nLocks' = nLocks + 1
             /\ locks' = [locks EXCEPT ![nLocks + 1] =
                          [alive |-> TRUE, owner |-> t, proc |-> P(t), loader |-> L(t), path |-> S(t),
                           inode |-> i, ctime |-> tl[t].ctime, closed |-> FALSE, chanOpen |-> TRUE,
                           jdkValid |-> TRUE]]
             /\ Goto(t, "idle")                            \* return new NativeFSLock
          \/ /\ Faults                                     \* tryLock threw IOException
             /\ Goto(t, "failclose") /\ UNCHANGED <<osLock, locks, nLocks>>
  /\ UNCHANGED <<entry, nextInode, lockHeld, tl, attempts, ext>>

\* L139-141: close the channel we opened (POSIX: releases every lock this process holds on i)
FailClose(t) ==
  /\ pc[t] = "failclose"
  /\ osLock' = ReleaseOnClose(P(t), tl[t].chan)
  /\ tl' = [tl EXCEPT ![t].chan = 0]
  /\ Goto(t, "clear")
  /\ UNCHANGED <<entry, nextInode, lockHeld, locks, nLocks, attempts, ext>>

\* L113-116 + L145-150
Clear(t) ==
  /\ pc[t] = "clear"
  /\ lockHeld' = IF UseLockHeld THEN [lockHeld EXCEPT ![L(t)] = @ \ {S(t)}] ELSE lockHeld
  /\ Goto(t, "idle")
  /\ UNCHANGED <<entry, nextInode, osLock, locks, nLocks, tl, attempts, ext>>

\* L200-214: close() of a lock owned by t (owner-only, as IndexWriter does)
StartClose(t, k) ==
  /\ pc[t] = "idle" /\ Live(k) /\ locks[k].owner = t /\ ~locks[k].closed
  /\ tl' = [tl EXCEPT ![t].k = k]
  /\ Goto(t, "closerel")
  /\ UNCHANGED <<entry, nextInode, osLock, lockHeld, locks, nLocks, attempts, ext>>

CloseRel(t) ==
  LET k == tl[t].k IN
  /\ pc[t] = "closerel"
  /\ IF locks[k].chanOpen /\ locks[k].jdkValid
       THEN /\ osLock' = [osLock EXCEPT ![locks[k].inode] = IF @ = locks[k].proc THEN NONE ELSE @]
            /\ locks' = [locks EXCEPT ![k].jdkValid = FALSE]
       ELSE UNCHANGED <<osLock, locks>>                 \* ClosedChannelException; continue
  /\ Goto(t, "closech")
  /\ UNCHANGED <<entry, nextInode, lockHeld, nLocks, tl, attempts, ext>>

CloseCh(t) ==
  LET k == tl[t].k IN
  /\ pc[t] = "closech"
  /\ IF locks[k].chanOpen
       THEN /\ osLock' = ReleaseOnClose(locks[k].proc, locks[k].inode)
            /\ locks' = [locks EXCEPT ![k].chanOpen = FALSE, ![k].jdkValid = FALSE]
       ELSE UNCHANGED <<osLock, locks>>
  /\ Goto(t, "closefin")
  /\ UNCHANGED <<entry, nextInode, lockHeld, nLocks, tl, attempts, ext>>

CloseFin(t) ==
  LET k == tl[t].k IN
  /\ pc[t] = "closefin"
  /\ locks' = [locks EXCEPT ![k].closed = TRUE]
  /\ lockHeld' = IF UseLockHeld THEN [lockHeld EXCEPT ![locks[k].loader] = @ \ {locks[k].path}]
                 ELSE lockHeld
  /\ Goto(t, "idle")
  /\ UNCHANGED <<entry, nextInode, osLock, nLocks, tl, attempts, ext>>

\* Thread.interrupt during I/O on the lock's channel closes it asynchronously
Interrupt(k) ==
  /\ Interrupts /\ Live(k) /\ ~locks[k].closed /\ locks[k].chanOpen
  /\ osLock' = ReleaseOnClose(locks[k].proc, locks[k].inode)
  /\ locks' = [locks EXCEPT ![k].chanOpen = FALSE, ![k].jdkValid = FALSE]
  /\ UNCHANGED <<entry, nextInode, lockHeld, nLocks, pc, tl, attempts, ext>>

ExtDelete(d) ==
  /\ ext < MaxExternal /\ entry[d] # 0
  /\ entry' = [entry EXCEPT ![d] = 0]
  /\ ext' = ext + 1
  /\ UNCHANGED <<nextInode, osLock, lockHeld, locks, nLocks, pc, tl, attempts>>

ExtRecreate(d) ==
  /\ ext < MaxExternal /\ entry[d] = 0 /\ nextInode <= MaxInode
  /\ entry' = [entry EXCEPT ![d] = nextInode]
  /\ nextInode' = nextInode + 1
  /\ ext' = ext + 1
  /\ UNCHANGED <<osLock, lockHeld, locks, nLocks, pc, tl, attempts>>

Next ==
  \/ \E t \in Threads :
       \/ StartObtain(t) \/ CreateFile(t) \/ RealPath(t) \/ Attr(t) \/ Add(t) \/ Open(t)
       \/ TryLock(t) \/ FailClose(t) \/ Clear(t)
       \/ (\E k \in LockIds : StartClose(t, k))
       \/ CloseRel(t) \/ CloseCh(t) \/ CloseFin(t)
  \/ \E k \in LockIds : Interrupt(k)
  \/ \E d \in Dirs : ExtDelete(d) \/ ExtRecreate(d)

Spec == Init /\ [][Next]_vars

-----------------------------------------------------------------------------
\* ensureValid() (L170-197) would return normally in this state
Valid(k) ==
  /\ Live(k) /\ ~locks[k].closed
  /\ (UseLockHeld => locks[k].path \in lockHeld[locks[k].loader])
  /\ locks[k].jdkValid /\ locks[k].chanOpen
  /\ entry[DirOf[locks[k].path]] # 0
  /\ Ctime(entry[DirOf[locks[k].path]]) = locks[k].ctime

\* A lock that passes ensureValid really holds the OS lock on the file its path names.
ValidImpliesOsHeld ==
  \A k \in LockIds : Valid(k) =>
     /\ osLock[locks[k].inode] = locks[k].proc
     /\ entry[DirOf[locks[k].path]] = locks[k].inode

\* No two locks that both pass ensureValid guard the same directory.
MutualExclusion ==
  \A k1, k2 \in LockIds :
     (k1 # k2 /\ Valid(k1) /\ Valid(k2)) => DirOf[locks[k1].path] # DirOf[locks[k2].path]

\* Every LOCK_HELD entry belongs to an unclosed lock or to an obtain in flight.
NoLeak ==
  \A l \in Loaders : \A s \in lockHeld[l] :
     \/ \E k \in LockIds : Live(k) /\ ~locks[k].closed /\ locks[k].loader = l /\ locks[k].path = s
     \/ \E t \in Threads : LoaderOf[t] = l /\ PathOf[t] = s /\ pc[t] \in {"open", "trylock", "failclose", "clear"}

\* Every OS lock belongs to an unclosed lock object with an open channel.
OsNoLeak ==
  \A i \in Inodes : osLock[i] # NONE =>
     \E k \in LockIds : Live(k) /\ ~locks[k].closed /\ locks[k].chanOpen /\ locks[k].inode = i
                       /\ locks[k].proc = osLock[i]

\* When everything is closed and every thread is idle, nothing stays locked.
IdleClean ==
  ((\A t \in Threads : pc[t] = "idle") /\ (\A k \in LockIds : Live(k) => locks[k].closed))
     => /\ \A l \in Loaders : lockHeld[l] = {}
        /\ \A i \in Inodes : osLock[i] = NONE

TypeOK ==
  /\ entry \in [Dirs -> 0..MaxInode]
  /\ osLock \in [Inodes -> Procs \cup {NONE}]
  /\ lockHeld \in [Loaders -> SUBSET Paths]
  /\ nLocks \in 0..MaxLocks

\* vacuity probes (must be violated): some lock becomes valid; an OverlappingFileLockException path is taken
NeverValid == \A k \in LockIds : ~Valid(k)
NeverTwoLive == ~(\E k1, k2 \in LockIds : k1 # k2 /\ Live(k1) /\ Live(k2) /\ ~locks[k1].closed /\ ~locks[k2].closed)
=============================================================================
