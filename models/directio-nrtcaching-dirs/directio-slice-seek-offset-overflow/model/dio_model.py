"""
Z3 (bit-vector, 64-bit two's complement = Java long) model of DirectIODirectory.DirectIOIndexInput
on Lucene main @ ea57360912ed.

File under test: lucene/misc/src/java/org/apache/lucene/misc/store/DirectIODirectory.java

Model element -> Java line mapping
----------------------------------
  ctor_top(S, BS, C)        333-344  top-level input: offset 0, length = channel.size(), filePos = -bufferSize,
                                     buffer.limit(0); C is buffer.capacity() of allocateBuffer(bufferSize, blockSize)
  capacity(BS, B, r)        372-376  ByteBuffer.allocateDirect(BS + B - 1).alignedSlice(B) for a native address with
                                     r = address mod B (JDK ByteBuffer.alignedSlice semantics, transcribed)
  ctor_slice(off, len, C)   347-365  slice/clone constructor: alignedStart = off - off % B, filePos = alignedStart - C,
                                     pendingDelta = off - alignedStart, buffer.limit(0); C = other.buffer.capacity()
  getFilePointer            386-399  raw = filePos + position - offset; assert raw == -C - max(pd,0) || raw >= 0;
                                     returns max(raw, 0)
  seek                      401-422  pending fill, then in-window reposition [filePos, filePos+limit] or seekInternal
  seekInternal              424-436  pendingDelta = -1; aligned refill(delta); position(delta)
  readByte                  444-450
  refill                    479-494  pending first fill path, then fill(bytesToRead)
  fill                      496-516  filePos += capacity; EOF check; clear; channel.read(buffer, filePos); flip
                                     JDK FileChannel.read for a DIRECT channel: position < 0 -> IllegalArgumentException
                                     ("Negative position"); position % alignment != 0 -> IOException ("Channel position
                                     (..) is not a multiple of the block size"); otherwise reads min(C, size - pos)
                                     bytes (regular file, no short read before EOF; -1 at EOF)
  readBytes                 518-533  loop: copy what is left, refill(remaining request)
  clone                     593-601  new slice-ctor state at (offset, length), then seekInternal(getFilePointer())
  slice                     603-612  bounds check, slice ctor at this.offset + offset

Specification (ghost) = the byte-array semantics every other Directory implements and BaseDirectoryTestCase tests:
an input is the window [off, off + len) of the file; g is the logical file pointer;
  readByte    in bounds iff g < len, returns file[off + g], g += 1; else EOFException
  readBytes n in bounds iff g + n <= len, returns file[off+g .. off+g+n), g += n; else EOFException
  seek x      0 <= x <= len sets g = x; x > len -> EOFException (then "undetermined state": the trace stops)
  slice o,l   child window [off+o, off+o+l), g = 0;  clone: same window, same g
  getFilePointer() == g after every successful operation.

Status codes (first exception ends the trace):
  OK, EOF, IAE_POS (Buffer.position(newPos > limit)), UNDERFLOW (BufferUnderflowException), NEGPOS (IAE "Negative
  position" from FileChannel.read), MISALIGN (IOException from the JDK direct-I/O alignment check), LOOPBOUND (readBytes
  needed more refills than the unrolling bound R; excluded from verdicts), HANG (readBytes iteration without progress:
  would loop forever), QUIRK (known, upstream-characterized behaviour: a slice serves bytes past its own length from the
  buffered window, see TestDirectIODirectory#testFirstFillEofSemanticsMatchEagerFill and
  #testSeekBeyondSliceLengthMatchesEagerBehaviour; the trace stops there but the bytes served are still checked).
"""
import os
from z3 import (BitVec, BitVecVal, If, And, Or, Not, SRem, Solver, sat, unsat, unknown, BoolVal, simplify)

# Vacuity mutants (DIO_MUTANT env var): each re-introduces a plausible bug and must make some obligation SAT.
#   fill_eof_ignores_n      : fill's EOF check drops "(offset + length) - filePos < bytesToRead"
#   pending_no_position     : refill's pending path does not position(parked)
#   seekinternal_keeps_pd   : seekInternal does not drop pendingDelta (the #16619 hazard its comment describes)
#   clone_no_seek           : clone() does not seekInternal(getFilePointer())
#   lim_ignores_eof         : channel.read always fills the whole buffer (limit = capacity)
#   window_excludes_end     : seek's in-window test uses < filePos + limit instead of <=
MUTANT = os.environ.get("DIO_MUTANT", "")

W = 64
OK, EOF, IAE_POS, UNDERFLOW, NEGPOS, MISALIGN, LOOPBOUND, HANG, QUIRK = range(9)
STATUS_NAMES = ["OK", "EOF", "IAE_POS", "UNDERFLOW", "NEGPOS", "MISALIGN", "LOOPBOUND", "HANG", "QUIRK"]


def V(x):
    return BitVecVal(x, W)


def jrem(x, B):
    """Java long remainder x % B for a power-of-two constant B (sign follows the dividend), without a divider."""
    assert B & (B - 1) == 0
    m = V(B - 1)
    return If(x >= V(0), x & m, -((-x) & m))


def smin(a, b):
    return If(a < b, a, b)


def smax(a, b):
    return If(a > b, a, b)


class Ctx:
    """Per-query constants: B (block size), S (channel size), shared by every input on the file."""

    def __init__(self, B, S):
        self.B = B
        self.S = S


        self.viol = []          # list of (name, z3 Bool) recorded along the trace

    def add(self, name, cond):
        self.viol.append((name, cond))


class St:
    FIELDS = ["off", "len", "fp", "p", "lim", "pd", "loaded", "g", "C", "status"]

    def __init__(self, top=False, **kw):
        for f in self.FIELDS:
            setattr(self, f, kw[f])
        self.top = top

    def copy(self, **kw):
        d = {f: getattr(self, f) for f in self.FIELDS}
        top = kw.pop("top", self.top)
        d.update(kw)
        return St(top=top, **d)

    def ok(self):
        return self.status == V(OK)


def merge(c, a, b):
    """If(c, a, b) field-wise."""
    return St(top=a.top, **{f: If(c, getattr(a, f), getattr(b, f)) for f in St.FIELDS})


def capacity(BS, B, r):
    """ByteBuffer.allocateDirect(BS + B - 1).alignedSlice(B), native address mod B == r (JDK source transcription)."""
    N = BS + V(B) - 1
    aligned_pos = If(r > 0, V(B) - r, V(0))
    lim_mod = jrem(r + N, B)
    aligned_lim = N - lim_mod
    cross = Or(aligned_pos > N, aligned_lim < V(0))
    return If(cross, V(0), aligned_lim - aligned_pos)


def ctor_top(ctx, BS, C):
    return St(top=True, off=V(0), len=ctx.S, fp=-BS, p=V(0), lim=V(0), pd=V(-1), loaded=V(0), g=V(0), C=C,
              status=V(OK))


def ctor_slice(ctx, off, ln, C, g=None, top=False):
    B = V(ctx.B)
    al = off - jrem(off, ctx.B)
    return St(top=top, off=off, len=ln, fp=al - C, p=V(0), lim=V(0), pd=off - al, loaded=V(0),
              g=V(0) if g is None else g, C=C, status=V(OK))


def raw_fp(st):
    return st.fp + st.p - st.off


def fp_assert_ok(st):
    raw = raw_fp(st)
    return Or(raw == -st.C - smax(st.pd, V(0)), raw >= V(0))


def get_fp(st):
    return smax(raw_fp(st), V(0))


def fill(ctx, st, n):
    """fill(bytesToRead): Java 496-516 (+ JDK FileChannel.read checks)."""
    B = V(ctx.B)
    fp1 = st.fp + st.C
    end = st.off + st.len
    eof = Or(fp1 > end, end - fp1 < n) if MUTANT != "fill_eof_ignores_n" else (fp1 > end)
    negpos = fp1 < V(0)
    mis = jrem(fp1, ctx.B) != V(0)
    rem = ctx.S - fp1
    lim1 = If(rem < st.C, smax(rem, V(0)), st.C) if MUTANT != "lim_ignores_eof" else st.C
    status = If(eof, V(EOF), If(negpos, V(NEGPOS), If(mis, V(MISALIGN), V(OK))))
    good = status == V(OK)
    out = st.copy(fp=fp1, status=status,
                  loaded=If(good, fp1, st.loaded),
                  p=If(good, V(0), st.p),
                  lim=If(good, lim1, st.lim))
    return merge(st.ok(), out, st)


def position(st, newp):
    """Buffer.position(newp): IllegalArgumentException when newp > limit."""
    bad = newp > st.lim
    out = st.copy(p=If(bad, st.p, newp), status=If(bad, V(IAE_POS), st.status))
    return merge(st.ok(), out, st)


def refill(ctx, st, n):
    """refill(bytesToRead): Java 479-494."""
    parked = st.pd
    a1 = st.copy(pd=V(-1))
    a2 = fill(ctx, a1, parked)
    a3 = position(a2, parked) if MUTANT != "pending_no_position" else a2
    done_early = And(a3.ok(), a3.lim - a3.p > V(0))
    a4 = fill(ctx, a3, n)
    a_res = merge(Or(done_early, Not(a3.ok())), a3, a4)
    b_res = fill(ctx, st, n)
    res = merge(st.pd >= V(0), a_res, b_res)
    return merge(st.ok(), res, st)


def finish(ctx, st, s2, inb, served_bad, opname):
    """Common spec checks after an operation. st = state before, s2 = after (g already the spec's new g)."""
    started = st.ok()
    completed = And(started, s2.ok())
    excepted = And(started, Not(s2.ok()), s2.status != V(LOOPBOUND))
    ctx.add("wrong_bytes", And(completed, inb, served_bad))
    ctx.add("spurious_exception", And(excepted, inb))
    # an out-of-bounds request must fail with EOFException (or end in the known slice quirk), nothing else
    ctx.add("wrong_exception", And(excepted, Not(inb), s2.status != V(EOF), s2.status != V(HANG)))
    ctx.add("hang", And(started, s2.status == V(HANG)))
    missing = And(completed, Not(inb))
    if st.top:
        ctx.add("missing_eof_top", missing)
    ctx.add("quirk_wrong_bytes", And(missing, served_bad))
    s3 = s2.copy(status=If(missing, V(QUIRK), s2.status))
    done = And(started, s3.ok())
    ctx.add("fp_mismatch", And(done, get_fp(s3) != s3.g))
    ctx.add("fp_assert", And(done, Not(fp_assert_ok(s3))))
    return s3


def read_byte(ctx, st):
    """readByte(): Java 444-450."""
    g0 = st.g
    inb = g0 < st.len
    s1 = merge(st.p >= st.lim, refill(ctx, st, V(1)), st)
    under = And(s1.ok(), s1.p >= s1.lim)
    idx = s1.loaded + s1.p
    s2 = s1.copy(p=If(under, s1.p, s1.p + 1), status=If(under, V(UNDERFLOW), s1.status), g=g0 + 1)
    s2 = merge(st.ok(), s2, st)
    return finish(ctx, st, s2, inb, idx != st.off + g0, "readByte")


def read_bytes(ctx, st, n, R=3):
    """readBytes(dst, 0, n): Java 518-533 with the loop unrolled for R refills."""
    g0 = st.g
    inb = g0 + n <= st.len
    cur = st.copy()
    toRead = n
    gcur = g0
    done = BoolVal(False)
    chunk_bad = BoolVal(False)
    for _ in range(R + 1):
        active = And(cur.ok(), Not(done))
        left = cur.lim - cur.p
        partial = left < toRead
        start_bad = cur.loaded + cur.p != cur.off + gcur
        chunk_bad = Or(chunk_bad,
                       And(active, partial, left > V(0), start_bad),
                       And(active, Not(partial), toRead > V(0), start_bad))
        fin = cur.copy(p=cur.p + toRead)
        pc = cur.copy(p=cur.lim)
        tr2 = toRead - left
        pr = refill(ctx, pc, tr2)
        noprog = And(pr.ok(), pr.lim - pr.p == V(0), left == V(0))
        pr = pr.copy(status=If(noprog, V(HANG), pr.status))
        nxt = merge(partial, pr, fin)
        gnext = If(partial, gcur + left, gcur + toRead)
        tnext = If(partial, tr2, V(0))
        cur = merge(active, nxt, cur)
        gcur = If(active, gnext, gcur)
        toRead = If(active, tnext, toRead)
        done = Or(done, And(active, Not(partial)))
    cur = cur.copy(status=If(And(cur.ok(), Not(done)), V(LOOPBOUND), cur.status))
    s2 = cur.copy(g=g0 + n)
    s2 = merge(st.ok(), s2, st)
    return finish(ctx, st, s2, inb, chunk_bad, "readBytes")


def seek_internal(ctx, st, x):
    """seekInternal(pos): Java 424-436."""
    B = V(ctx.B)
    a = x + st.off
    al = a - jrem(a, ctx.B)
    s1 = st.copy(pd=V(-1) if MUTANT != "seekinternal_keeps_pd" else st.pd, fp=al - st.C)
    d = a - al
    s2 = refill(ctx, s1, d)
    s3 = position(s2, d)
    return merge(st.ok(), s3, st)


def seek(ctx, st, x):
    """seek(pos): Java 401-422. x >= 0 is a precondition of the query."""
    inb = x <= st.len
    ctx.add("fp_assert", And(st.ok(), Not(fp_assert_ok(st))))
    same = x == get_fp(st)
    parked = st.pd
    q1 = st.copy(pd=V(-1))
    q2 = fill(ctx, q1, parked)
    q3 = position(q2, parked)
    t0 = merge(st.pd >= V(0), q3, st)
    a = x + st.off
    inwin = And(a >= t0.fp, a <= t0.fp + t0.lim) if MUTANT != "window_excludes_end" else And(a >= t0.fp, a < t0.fp + t0.lim)
    w = merge(t0.ok(), t0.copy(p=a - t0.fp), t0)
    si = seek_internal(ctx, t0, x)
    moved = merge(inwin, w, si)
    s2 = merge(same, st, moved)
    s2 = merge(st.ok(), s2, st).copy(g=x)
    completed = And(st.ok(), s2.ok())
    ctx.add("seek_assert", And(completed, get_fp(s2) != x))     # Java 421 assert pos == getFilePointer()
    return finish(ctx, st, s2, inb, BoolVal(False), "seek")


def clone(ctx, st):
    """clone(): Java 593-601; the clone's spec position is the parent's g."""
    fpv = get_fp(st)
    c0 = ctor_slice(ctx, st.off, st.len, st.C, top=st.top)
    c1 = (seek_internal(ctx, c0, fpv) if MUTANT != "clone_no_seek" else c0).copy(g=st.g)
    c1 = merge(st.ok(), c1, st)
    ctx.add("clone_exception", And(st.ok(), Not(c1.ok()), st.g <= st.len))
    started = st.ok()
    done = And(started, c1.ok())
    ctx.add("fp_mismatch", And(done, get_fp(c1) != c1.g))
    ctx.add("fp_assert", And(done, Not(fp_assert_ok(c1))))
    return c1


def slice_(ctx, st, o, l):
    """slice(desc, o, l), legal bounds as precondition. The child's capacity equals the parent's capacity when that is
    a multiple of B (alignedSlice of C + B - 1 bytes; capacity(0) = 0), so C is inherited."""
    child = ctor_slice(ctx, st.off + o, l, st.C, top=False)
    return merge(st.ok(), child, st.copy(top=False))
