import ida_auto, idc, idautils, ida_entry, ida_funcs, ida_ua

ida_auto.auto_wait()

lines = []
# 1) 列出所有导出符号
for i in range(ida_entry.get_entry_qty()):
    ord_ = ida_entry.get_entry_ordinal(i)
    ea = ida_entry.get_entry(ord_)
    name = ida_entry.get_entry_name(ord_)
    if name and ("Command" in name or "command" in name or "JNI" in name or "Native" in name):
        lines.append("EXPORT %s %s" % (hex(ea), name))

# 2) 找 doCommandNative 并 dump
target = None
for i in range(ida_entry.get_entry_qty()):
    ord_ = ida_entry.get_entry_ordinal(i)
    name = ida_entry.get_entry_name(ord_)
    if name and "doCommandNative" in name:
        target = ida_entry.get_entry(ord_)
        lines.append("TARGET %s %s" % (hex(target), name))
        break

if target:
    lines.append("---- DISASM ----")
    ea = target
    end = ida_funcs.get_func(target).end_ea if ida_funcs.get_func(target) else target + 0x400
    cnt = 0
    while ea < end and cnt < 600:
        lines.append("%s  %s" % (hex(ea), idc.GetDisasm(ea)))
        n = idc.get_item_size(ea)
        ea += max(n, 4)
        cnt += 1

with open("D:/sgmt_exports.txt", "w", encoding="utf-8") as fh:
    fh.write("\n".join(lines) + "\n")
print("lines", len(lines))
idc.qexit(0)
