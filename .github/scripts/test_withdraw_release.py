import unittest
from unittest.mock import DEFAULT, Mock, patch

import withdraw_release as withdraw


REPO = "lmc2007/TMallKey"
BASE = f"https://github.com/{REPO}/releases"


def release(version, **extra):
    tag = f"v{version}"
    return {
        "id": int(version.split(".")[-1]),
        "tag_name": tag,
        "html_url": f"{BASE}/tag/{tag}",
        "draft": False,
        "prerelease": False,
        "assets": [{
            "state": "uploaded",
            "browser_download_url": f"{BASE}/download/{tag}/TMKey-{version}.apk",
            "size": 1024,
            "digest": f"sha256:{'a' * 64}",
        }],
        **extra,
    }


def manifest(version, code, **extra):
    public = release(version)
    return {
        "versionName": version,
        "versionCode": code,
        "isReady": True,
        "releaseUrl": public["html_url"],
        "assets": [{
            "url": public["assets"][0]["browser_download_url"],
            "size": 1024,
            "sha256": "a" * 64,
        }],
        **extra,
    }


class WithdrawReleaseTest(unittest.TestCase):
    def setUp(self):
        self.latest = release("2.1.12")
        self.current = manifest("2.1.12", 23)
        self.prior = manifest("2.1.10", 21)

    def test_skip_unavailable_or_unpublished_versions(self):
        history = [manifest("2.1.11", 22), self.current, self.prior, manifest("2.1.9", 20)]
        for unavailable in ({"assets": []}, {"draft": True}, {"prerelease": True}):
            with self.subTest(unavailable=unavailable):
                public = [release("2.1.11", **unavailable), release("2.1.9"), release("2.1.10")]
                self.assertEqual(withdraw.previous_manifest(history, public, self.current), self.prior)

    def test_skip_replaced_apk_with_stale_checksum(self):
        replaced = release("2.1.11")
        replaced["assets"][0]["digest"] = f"sha256:{'b' * 64}"
        history = [manifest("2.1.11", 22), self.prior]
        self.assertEqual(withdraw.previous_manifest(history, [replaced, release("2.1.10")], self.current), self.prior)

    def test_refuse_when_no_older_usable_release_exists(self):
        with self.assertRaisesRegex(ValueError, "回退"):
            withdraw.previous_manifest([self.current, self.prior], [self.latest], self.current)

    @patch.multiple(withdraw, autospec=True, github=DEFAULT, git=DEFAULT,
                    manifest_history=DEFAULT, load_manifest=DEFAULT,
                    write_and_push=DEFAULT, output=DEFAULT)
    def test_dry_run_checks_without_mutating(self, **mocks):
        self.configure(mocks)
        withdraw.run(REPO, "v2.1.12", dry_run=True)
        self.assertEqual(mocks["github"].call_count, 2)
        mocks["write_and_push"].assert_not_called()
        mocks["output"].assert_not_called()

    @patch.multiple(withdraw, autospec=True, github=DEFAULT, git=DEFAULT,
                    manifest_history=DEFAULT, load_manifest=DEFAULT,
                    write_and_push=DEFAULT, output=DEFAULT)
    def test_target_mismatch_leaves_both_unchanged(self, **mocks):
        self.configure(mocks)
        with self.assertRaisesRegex(ValueError, "Latest"):
            withdraw.run(REPO, "v2.1.11", dry_run=False)
        self.assertEqual(mocks["github"].call_count, 1)
        mocks["write_and_push"].assert_not_called()

    @patch.multiple(withdraw, autospec=True, github=DEFAULT, git=DEFAULT,
                    manifest_history=DEFAULT, load_manifest=DEFAULT,
                    write_and_push=DEFAULT, output=DEFAULT)
    def test_only_draft_after_manifest_is_pushed_and_allow_retry(self, **mocks):
        self.configure(mocks)
        steps = Mock()
        steps.attach_mock(mocks["github"], "api")
        steps.attach_mock(mocks["write_and_push"], "push")
        steps.attach_mock(mocks["output"], "output")
        mocks["write_and_push"].side_effect = RuntimeError("push rejected")
        with self.assertRaisesRegex(RuntimeError, "push rejected"):
            withdraw.run(REPO, "v2.1.12", dry_run=False)
        self.assertEqual(mocks["github"].call_count, 2)

        mocks["write_and_push"].side_effect = None
        self.configure(mocks, committed=True)
        withdraw.run(REPO, "v2.1.12", dry_run=False)
        self.assertEqual([call[0] for call in steps.mock_calls], ["api", "api", "push", "api", "api", "output", "api"])
        self.assertEqual(steps.mock_calls[-1].args, (f"repos/{REPO}/releases/12", "--method", "PATCH", "-F", "draft=true"))

    def configure(self, mocks, committed=False):
        mocks["git"].return_value = "release: 撤回 v2.1.12" if committed else "release: 发布 v2.1.12"
        mocks["load_manifest"].return_value = self.prior if committed else self.current
        mocks["manifest_history"].return_value = [self.prior]
        mocks["github"].side_effect = [self.latest, [[release("2.1.10")]], {**self.latest, "draft": True}]


if __name__ == "__main__":
    unittest.main()
