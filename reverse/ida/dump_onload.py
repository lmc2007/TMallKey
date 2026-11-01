import ida_auto, idc, ida_bytes, ida_funcs, idautils

ida_auto.auto_wait()

out = []
ea = 0x87A0
f = ida_funcs.get_func(ea)
end = f.end_ea if f else ea + 0x400
cur = ea
n = 0
while cur < end and n < 500:
    m = idc.print_insn_mnem(cur)
    o0 = idc.print_operand(cur, 0)
    o1 = idc.print_operand(cur, 1)
    o2 = idc.print_operand(cur, 2)
    out.append("%s  %-8s %s%s%s" % (hex(cur), m, o0,
                                    (", " + o1) if o1 else "",
                                    (", " + o2) if o2 else ""))
    nxt = idc.next_head(cur, end)
    if nxt <= cur:
        break
    cur = nxt
    n += 1

text_ranges = []
for s in idautils.Segments():
    if idc.get_segm_name(s) == ".text":
        text_ranges.append((idc.get_segm_start(s), idc.get_segm_end(s)))

def in_text(v):
    for a, b in text_ranges:
        if a <= v < b:
            return True
    return False

out.append("---- ptrs in data ----")
for s in idautils.Segments():
    name = idc.get_segm_name(s)
    if name not in (".data.rel.ro", ".data"):
        continue
    a, b = idc.get_segm_start(s), idc.get_segm_end(s)
    cur = a
    cnt = 0
    while cur + 8 <= b and cnt < 80:
        v = ida_bytes.get_qword(cur)
        if in_text(v):
            out.append("%s %s -> %s" % (name, hex(cur), hex(v)))
            cnt += 1
        cur += 8

with open("D:/sgmt_onload.txt", "w", encoding="utf-8") as fh:
    fh.write("\n".join(out) + "\n")
print("ok")
idc.qexit(0)
