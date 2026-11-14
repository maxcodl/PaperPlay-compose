import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(__file__))
import release as r


def c(subject, sha="a" * 40, body=""):
    return r.parse_commit(sha, subject, body)


def plan(tags=(), since=None, reachable=(), head=(), channel="beta", override="auto", log=None):
    """since maps a ref to its commits; unknown refs have none."""
    table = since or {}
    return r.make_plan(r.parse_tags(tags), lambda ref: table.get(ref, []), lambda t: t in reachable, list(head), channel, override, 150)


class Parsing(unittest.TestCase):
    def test_types_and_bumps(self):
        self.assertEqual(c("feat: add updater").bump, r.MINOR)
        self.assertEqual(c("fix(player): stop the crash").bump, r.PATCH)
        self.assertEqual(c("perf: faster covers").bump, r.PATCH)
        self.assertEqual(c("docs: rewrite the readme").bump, r.NONE)
        self.assertEqual(c("chore(release): v1.3.0").bump, r.NONE)
        self.assertEqual(c("feat!: drop the old backup format").bump, r.MAJOR)
        self.assertEqual(c("fix: x", body="BREAKING CHANGE: new format").bump, r.MAJOR)
        self.assertEqual(c("feat: x", body="- rules: feat minor, ! or BREAKING CHANGE major").bump, r.MINOR)

    def test_no_type_is_a_patch_and_keeps_the_whole_subject(self):
        x = c("Lists glide, covers fade in")
        self.assertEqual((x.kind, x.bump, x.text), (None, r.PATCH, "Lists glide, covers fade in"))

    def test_words_before_a_colon_are_not_types(self):
        self.assertIsNone(c("Windows build: also run createDistributable").kind)
        self.assertIsNone(c("Checklist: audit of the code").kind)

    def test_type_is_case_insensitive(self):
        self.assertEqual(c("Docs: rewrite the README").kind, "docs")
        self.assertEqual(c("Feat: add a thing").kind, "feat")


class Versions(unittest.TestCase):
    def test_first_release_is_exactly_the_first_version(self):
        p = plan(since={r.FIRST_SINCE: [c("fix: x")]}, channel="stable")
        self.assertEqual((p.release, p.version), (True, "1.3.0"))
        p = plan(since={r.FIRST_SINCE: [c("feat: x"), c("feat!: y")]}, channel="stable")
        self.assertEqual(p.version, "1.3.0")

    def test_bump_levels_from_the_last_stable(self):
        t = ["v1.3.0"]
        self.assertEqual(plan(t, {"v1.3.0": [c("fix: a")]}, channel="stable").version, "1.3.1")
        self.assertEqual(plan(t, {"v1.3.0": [c("fix: a"), c("feat: b")]}, channel="stable").version, "1.4.0")
        self.assertEqual(plan(t, {"v1.3.0": [c("feat: b"), c("fix!: c")]}, channel="stable").version, "2.0.0")

    def test_only_docs_and_chores_do_not_release(self):
        p = plan(["v1.3.0"], {"v1.3.0": [c("docs: a"), c("chore: b"), c("ci: c")]}, channel="stable")
        self.assertFalse(p.release)
        self.assertEqual(p.version, "1.3.0-dev")  # a build that is not published still gets a label

    def test_manual_bump_releases_without_commits(self):
        p = plan(["v1.3.0"], {"v1.3.0": [c("docs: a")]}, channel="stable", override="minor")
        self.assertEqual((p.release, p.version), (True, "1.4.0"))

    def test_an_existing_stable_tag_is_not_released_again(self):
        p = plan(["v1.3.0", "v1.3.1"], {"v1.3.1": []}, channel="stable")
        self.assertFalse(p.release)

    def test_a_commit_that_already_has_a_release_is_skipped(self):
        p = plan(since={r.FIRST_SINCE: [c("fix: a")]}, head=["v1.3.0-beta.1"])
        self.assertFalse(p.release)


class Betas(unittest.TestCase):
    def test_first_beta_of_a_version(self):
        p = plan(since={r.FIRST_SINCE: [c("fix: a")]})
        self.assertEqual((p.release, p.version, p.tag), (True, "1.3.0-beta.1", "v1.3.0-beta.1"))

    def test_numbering_continues_and_notes_cover_only_the_new_commits(self):
        # a feat after 1.3.1-beta.1 moves the base to 1.4.0, and the numbering starts again
        since = {"v1.3.0": [c("fix: a"), c("feat: new")], "v1.3.1-beta.1": [c("feat: new")]}
        p = plan(["v1.3.0", "v1.3.1-beta.1"], since, reachable=["v1.3.1-beta.1"])
        self.assertEqual((p.version, p.previous), ("1.4.0-beta.1", "v1.3.0"))
        since = {"v1.3.0": [c("fix: a")], "v1.3.1-beta.1": [c("fix: b")]}
        p = plan(["v1.3.0", "v1.3.1-beta.1"], since, reachable=["v1.3.1-beta.1"])
        self.assertEqual((p.version, p.previous), ("1.3.1-beta.2", "v1.3.1-beta.1"))
        self.assertEqual([x.text for x in p.commits], ["b"])

    def test_nothing_new_since_the_last_beta_skips(self):
        since = {"v1.3.0": [c("fix: a")], "v1.3.1-beta.1": [c("docs: only docs")]}
        p = plan(["v1.3.0", "v1.3.1-beta.1"], since, reachable=["v1.3.1-beta.1"])
        self.assertFalse(p.release)

    def test_a_beta_tag_that_is_not_an_ancestor_is_ignored_for_the_range_but_still_numbered(self):
        since = {"v1.3.0": [c("fix: a")]}
        p = plan(["v1.3.0", "v1.3.1-beta.4"], since, reachable=[])
        self.assertEqual((p.version, p.previous), ("1.3.1-beta.5", "v1.3.0"))


class Notes(unittest.TestCase):
    def test_sections_are_grouped_and_noise_is_hidden(self):
        commits = [c("feat: add the updater", "1" * 40), c("fix: crash on restore", "2" * 40), c("docs: readme", "3" * 40),
                   c("chore: bump", "4" * 40), c("Lists glide", "5" * 40), c("feat!: new backup format", "6" * 40)]
        text = r.notes(commits, "v1.2.0", "v1.3.0")
        order = [text.index(h) for h in ("### Breaking changes", "### Added", "### Fixed", "### Documentation", "### Other changes")]
        self.assertEqual(order, sorted(order))
        self.assertIn("- Add the updater ([`1111111`]", text)
        self.assertIn("- New backup format", text)
        self.assertNotIn("bump", text)
        self.assertIn("compare/v1.2.0...v1.3.0", text)

    def test_empty_notes_say_so(self):
        self.assertIn("No user-facing changes.", r.notes([c("chore: x")], "v1.2.0", "v1.3.0"))


class Changelog(unittest.TestCase):
    HEADER = "# Changelog\n\nAll notable changes.\n\n"

    def test_newest_goes_first_and_repeats_are_ignored(self):
        one = r.changelog(self.HEADER, "1.3.0", "### Added\n- a\n", "2026-11-14")
        two = r.changelog(one, "1.4.0", "### Fixed\n- b\n", "2026-12-01")
        self.assertLess(two.index("## [1.4.0]"), two.index("## [1.3.0]"))
        self.assertEqual(r.changelog(two, "1.4.0", "x", "2027-01-01"), two)
        self.assertTrue(two.startswith("# Changelog"))


if __name__ == "__main__":
    unittest.main()
