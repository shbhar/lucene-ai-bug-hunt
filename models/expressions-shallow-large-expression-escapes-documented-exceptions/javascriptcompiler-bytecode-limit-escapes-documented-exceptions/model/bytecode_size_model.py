"""Size model of the bytecode JavascriptCompiler generates for evaluate(DoubleValues[]).

Purpose: predict, for each family of well-formed shallow expressions used by the harness, the input
size at which a JVM class-file limit is crossed, and so which inputs must fail and which must
compile. The edge sweep (harness ExprLimitsHarness 'edges' mode) then checks every prediction
exactly on the stock jars (main: java.lang.classfile; branch_10_6 and 10.5.2: ASM 9.6).

Model element -> Java line (main a9779ef3c193, lucene/expressions/src/java/org/apache/lucene/
expressions/js/JavascriptCompiler.java):
  leaf variable x      -> visitExternal, lines 471-478: aload_1, loadConstant(index), aaload,
                          invokevirtual DoubleValues.doubleValue  = 1 + 1 + 1 + 3 = 6 bytes
                          (index 0 -> iconst_0, 1 byte)
  leaf constant i.5    -> visitNumeric, line 422: loadConstant(double) -> ldc2_w = 3 bytes,
                          2 constant-pool slots per distinct double
  binary +             -> pushBinaryOp, line 777: dadd = 1 byte
  method epilogue      -> generateClass lines 377-392: dreturn (1) + handler aload_0 (1) +
                          invokestatic patchStackTrace (3) + athrow (1) = 6 bytes, of which
                          dreturn closes the try block
  call wide(127 args)  -> visitExternal lines 432-468: ldc condy (2 or 3), args, invokevirtual
                          MethodHandle.invokeExact (3); operand stack holds the MethodHandle
                          (1 slot) plus the already evaluated arguments (2 slots each)
JVM limits (JVMS 4.7.3 and 4.4): code_length < 65536; constant_pool_count is a u2; max_stack is a u2.
ASM 9.6 limit (org.objectweb.asm.Frame): the abstract output stack top is a Java 'short', so a
basic block whose operand stack grows past 32767 slots overflows the index (observed AIOOBE -32768).
"""

CODE_LIMIT = 65535  # max code_length
U2 = 65535
ASM_SHORT = 32767


def sum_x_code_length(n):  # n leaves of the same variable, n-1 dadd
    return 6 * n + (n - 1) + 6


def const_code_length(n):  # n distinct non-integral doubles, n-1 dadd
    return 3 * n + (n - 1) + 6


def wide_max_stack(levels, arity=127):  # nested wide(1,...,1, inner), innermost 'x'
    per_level = 1 + 2 * (arity - 1)
    return per_level * levels + 2


def first_failing(f, limit):
    n = 1
    while f(n) <= limit:
        n += 1
    return n


if __name__ == "__main__":
    print("sum-x: code_length(n) = 7n+5; check n=9400 ->", sum_x_code_length(9400), "(main reports 65805)")
    print("  last compiling n =", first_failing(sum_x_code_length, CODE_LIMIT) - 1,
          " first failing n =", first_failing(sum_x_code_length, CODE_LIMIT))
    print("const: code_length(n) = 4n+5; check n=16400 ->", const_code_length(16400), "(main reports 65605)")
    print("  last compiling n =", first_failing(const_code_length, CODE_LIMIT) - 1,
          " first failing n =", first_failing(const_code_length, CODE_LIMIT))
    print("wide-127: max_stack(L) = 253L+2")
    print("  ASM short overflow: last OK L =", first_failing(wide_max_stack, ASM_SHORT) - 1,
          " first failing L =", first_failing(wide_max_stack, ASM_SHORT),
          " (max_stack", wide_max_stack(first_failing(wide_max_stack, ASM_SHORT)), ")")
    print("  u2 max_stack overflow: last OK L =", first_failing(wide_max_stack, U2) - 1,
          " first failing L =", first_failing(wide_max_stack, U2))
    print("  code bytes at L=140 (upper bound, ldc_w condy):", 140 * (126 + 3 + 3) + 6 + 6)
