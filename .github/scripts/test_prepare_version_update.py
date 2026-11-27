import importlib.util
import json
import subprocess
import tempfile
import unittest
from contextlib import redirect_stdout
from io import StringIO
from pathlib import Path
from types import SimpleNamespace
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


class PrepareAfterWithdrawalTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.manifest = self.root / "update/stable.json"
        self.changelogs = self.manifest.parent / "changelogs"
        self.gradle = self.root / "fktm-android/app/build.gradle.kts"
        self.changelogs.mkdir(parents=True)
        self.gradle.parent.mkdir(parents=True)
        self.gradle.write_text('android {\n    versionCode = 23\n    versionName = "2.1.12"\n}\n', encoding="utf-8")
        self.git("init", "--initial-branch=main", "--quiet")
        self.enterContext(patch.multiple(prepare, ROOT=self.root, MANIFEST_FILE=self.manifest,
                                         GRADLE_FILE=self.gradle, CHANGELOG_DIR=self.changelogs))
        self.enterContext(patch.object(prepare, "os", SimpleNamespace(environ={})))
        self.enterContext(patch.object(prepare, "remote_tag_exists", return_value=False))
        self.enterContext(redirect_stdout(StringIO()))

    def git(self, *args):
        subprocess.run(["git", "-c", "user.name=Test", "-c", "user.email=test@example.com", *args],
                       cwd=self.root, check=True, stdout=subprocess.DEVNULL)

    def commit(self, subject):
        self.git("add", ".")
        self.git("commit", "--quiet", "--allow-empty", "-m", subject)

    def write_manifest(self, version, code, *, ready=True):
        release = "https://github.com/lmc2007/TMallKey/releases"
        self.manifest.write_text(json.dumps({
            "channel": "stable",
            "versionName": version,
            "versionCode": code,
            "isReady": ready,
            "releaseUrl": f"{release}/tag/v{version}" if ready else None,
            "assets": [{"url": f"{release}/download/v{version}/TMKey-{version}.apk"}] if ready else [],
        }), encoding="utf-8")

    def history_with_withdrawal(self):
        self.write_manifest("2.1.10", 21)
        self.commit("release: 发布 v2.1.10 (versionCode 21)")
        self.commit("fix: 补上此前撤回版本中的修复")
        self.write_manifest("2.1.12", 23)
        self.commit("release: 发布 v2.1.12 (versionCode 23)")
        self.commit("fix: 下一次发布包含的修复")
        self.write_manifest("2.1.10", 21)
        self.commit("release: 撤回 v2.1.12")

    def test_existing_withdrawal_prepares_next_version_from_gradle(self):
        self.history_with_withdrawal()
        prepare.main()
        self.assertEqual(prepare.read_versions(), (24, "2.1.13"))
        manifest = prepare.load_manifest(self.manifest)
        self.assertEqual((manifest["versionCode"], manifest["versionName"]), (24, "2.1.13"))
        self.assertFalse(manifest["isReady"])
        self.assertEqual(manifest["assets"], [])
        changelog = (self.changelogs / "24.changelog").read_text(encoding="utf-8")
        self.assertIn("补上此前撤回版本中的修复", changelog)
        self.assertIn("下一次发布包含的修复", changelog)

    def test_unrelated_version_mismatch_is_rejected(self):
        self.write_manifest("2.1.10", 21)
        self.commit("Update stable.json")
        self.assert_version_mismatch()

    def test_unready_or_newer_manifest_is_rejected_even_after_withdrawal(self):
        for version, code, ready in (("2.1.10", 21, False), ("2.1.14", 25, True)):
            with self.subTest(version=version, code=code, ready=ready):
                self.write_manifest(version, code, ready=ready)
                self.commit("release: 撤回 v2.1.12")
                self.assert_version_mismatch()

    def assert_version_mismatch(self):
        stderr = StringIO()
        with patch("sys.stderr", stderr), self.assertRaises(SystemExit):
            prepare.main()
        self.assertIn("不一致", stderr.getvalue())
        self.assertEqual(prepare.read_versions(), (23, "2.1.12"))
        self.assertFalse((self.changelogs / "24.changelog").exists())


if __name__ == "__main__":
    unittest.main()
