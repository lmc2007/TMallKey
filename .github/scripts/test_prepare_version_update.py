import importlib.util
import unittest
from pathlib import Path
from unittest.mock import patch


SCRIPT = Path(__file__).with_name("prepare-version-update.py")
spec = importlib.util.spec_from_file_location("prepare_version_update", SCRIPT)
prepare = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prepare)


class ChangelogInputTest(unittest.TestCase):
    def test_manual_expands_web_input_into_paragraphs(self):
        body, summary = prepare.build_changelog("2.1.9", "manual", r"- 修复开锁\n- 优化重连\n\n第二段")
        self.assertEqual(body, "# v2.1.9\n\n- 修复开锁\n- 优化重连\n\n第二段\n")
        self.assertEqual(summary, "修复开锁")

    def test_auto_extra_expands_web_input_into_bullets(self):
        with patch.object(prepare, "collect_changelog", return_value={"新增": ["自动整理"]}):
            body, summary = prepare.build_changelog("2.1.9", "auto+extra", r"补充一\n- 补充二")
        self.assertEqual(body, "# v2.1.9\n\n新增：\n- 自动整理\n\n其他：\n- 补充一\n- 补充二\n")
        self.assertEqual(summary, "自动整理")

    def test_true_multiline_preserves_literal_backslash_n(self):
        body, summary = prepare.build_changelog("2.1.9", "manual", "- 示例 `\\n`\r\n\r\n第二段")
        self.assertEqual(body, "# v2.1.9\n\n- 示例 `\\n`\n\n第二段\n")
        self.assertEqual(summary, r"示例 `\n`")

    def test_escaped_separator_stays_literal(self):
        body, summary = prepare.build_changelog("2.1.9", "manual", r"- 示例 `\\n`\n- 第二条")
        self.assertEqual(body, "# v2.1.9\n\n- 示例 `\\n`\n- 第二条\n")
        self.assertEqual(summary, r"示例 `\n`")


if __name__ == "__main__":
    unittest.main()
