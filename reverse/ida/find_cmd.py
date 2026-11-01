import ida_auto, ida_ua, ida_funcs, idautils, idc

ida_auto.auto_wait()

TARGETS = {0x111D5: "70101", 0x111E6: "70102"}
rows = []
for seg_ea in idautils.Segments():
    name = idc.get_segm_name(seg_ea)
    if name != ".text":
        continue
    ea = idc.get_segm_start(seg_ea)
    end = idc.get_segm_end(seg_ea)
    while ea < end:
        insn = ida_ua.insn_t()
        n = ida_ua.decode_insn(insn, ea)
        if n <= 0:
            ea += 4
            continue
        for op in insn.ops:
            if op.type == 2 and op.value in TARGETS:
                f = ida_funcs.get_func(ea)
                rows.append("%s %s %s func=%s:%s off=%s" % (
                    hex(ea), insn.get_canon_mnem(), TARGETS[op.value],
                    hex(f.start_ea) if f else "?", ida_funcs.get_func_name(f.start_ea) if f else "?",
                    hex(ea - f.start_ea) if f else "?"))
        ea += n

with open("D:/sg70101_hits.txt", "w", encoding="utf-8") as fh:
    fh.write("\n".join(rows[:300]) + "\nTOTAL %d\n" % len(rows))
print("hits", len(rows))
idc.qexit(0)
