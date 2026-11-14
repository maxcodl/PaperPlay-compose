#!/usr/bin/env python3
"""Works out the next version from the commit subjects and writes the release notes.

  release.py plan --channel beta|stable [--bump auto|patch|minor|major] [--github-output]
  release.py notes --channel beta|stable --out notes.md
  release.py changelog --version 1.3.0 --notes notes.md [--file CHANGELOG.md]

Commit subjects start with a type: feat (minor), fix or perf (patch), `!` or BREAKING CHANGE (major).
A subject without a type counts as a patch. docs, chore, ci, build, style, test and refactor never release by themselves.
Versions come from git tags (v1.3.0 and v1.3.0-beta.7); nothing is stored in the build files.
"""
import argparse
import datetime
import os
import re
import subprocess
import sys
from dataclasses import dataclass, field

REPO_URL = "https://github.com/maxcodl/PaperPlay-compose"
FIRST_VERSION = (1, 3, 0)  # the first release is exactly this, whatever the commits are
FIRST_SINCE = "e7a6417"  # until a stable tag exists, notes start after this commit
IGNORED = ("8e9b526",)  # commits that never appear in the notes or decide a version

KNOWN = {"feat", "fix", "perf", "docs", "chore", "ci", "build", "style", "test", "refactor"}
SECTIONS = [  # (heading, commit types); unprefixed commits go to "Other changes"
    ("Breaking changes", None),
    ("Added", {"feat"}),
    ("Fixed", {"fix"}),
    ("Improved", {"perf"}),
    ("Documentation", {"docs"}),
    ("Other changes", {None}),
]
MAJOR, MINOR, PATCH, NONE = 3, 2, 1, 0
_PREFIX = re.compile(r"^(?P<type>[A-Za-z]+)(?:\((?P<scope>[^)]*)\))?(?P<bang>!)?:\s+(?P<text>\S.*)$")
_STABLE = re.compile(r"^v(\d+)\.(\d+)\.(\d+)$")
_BETA = re.compile(r"^v(\d+)\.(\d+)\.(\d+)-beta\.(\d+)$")


@dataclass
class Commit:
    sha: str
    kind: str | None  # a type from KNOWN, or None when the subject has no type
    text: str
    breaking: bool = False

    @property
    def bump(self) -> int:
        if self.breaking:
            return MAJOR
        if self.kind == "feat":
            return MINOR
        if self.kind in ("fix", "perf") or self.kind is None:
            return PATCH
        return NONE


def parse_commit(sha: str, subject: str, body: str = "") -> Commit:
    subject = subject.strip()
    breaking = "BREAKING CHANGE" in body or "BREAKING-CHANGE" in body
    m = _PREFIX.match(subject)
    if m and m["type"].lower() in KNOWN:
        return Commit(sha, m["type"].lower(), m["text"].strip(), breaking or bool(m["bang"]))
    return Commit(sha, None, subject, breaking)


@dataclass
class Tags:
    stable: set = field(default_factory=set)  # {(1, 3, 0)}
    betas: dict = field(default_factory=dict)  # {(1, 3, 0): {1, 2}}


def parse_tags(names) -> Tags:
    t = Tags()
    for n in names:
        if m := _STABLE.match(n):
            t.stable.add(tuple(int(x) for x in m.groups()))
        elif m := _BETA.match(n):
            g = [int(x) for x in m.groups()]
            t.betas.setdefault(tuple(g[:3]), set()).add(g[3])
    return t


def vs(v) -> str:
    return ".".join(str(x) for x in v)


def tag_of(base, beta=None) -> str:
    return f"v{vs(base)}" + (f"-beta.{beta}" if beta else "")


def next_base(last, commits, override="auto"):
    level = {"major": MAJOR, "minor": MINOR, "patch": PATCH}.get(override) or max((c.bump for c in commits), default=NONE)
    if level == NONE:
        return None
    if last is None:
        return FIRST_VERSION
    major, minor, patch = last
    base = (major + 1, 0, 0) if level == MAJOR else (major, minor + 1, 0) if level == MINOR else (major, minor, patch + 1)
    return max(base, FIRST_VERSION)


@dataclass
class Plan:
    release: bool
    version: str = ""
    base: str = ""
    tag: str = ""
    channel: str = ""
    previous: str = ""
    code: int = 0
    commits: list = field(default_factory=list)


def make_plan(tags, log_since, reachable, head_tags, channel, override, code) -> Plan:
    """log_since(ref) lists the commits after ref; reachable(tag) says whether the tag is an ancestor of HEAD."""
    last = max(tags.stable, default=None)
    stable_ref = tag_of(last) if last else FIRST_SINCE
    since_stable = log_since(stable_ref)
    base = next_base(last, since_stable, override)
    now = vs(last or FIRST_VERSION)
    skip = Plan(False, f"{now}-dev", now, "", channel, "", code)  # still built, never published
    if base is None or head_tags:  # nothing to release, or this commit already has its release
        return skip
    if channel == "stable":
        if base in tags.stable:
            return skip
        return Plan(True, vs(base), vs(base), tag_of(base), channel, stable_ref, code, since_stable)
    existing = tags.betas.get(base, set())
    reachable_betas = sorted(n for n in existing if reachable(tag_of(base, n)))
    previous = tag_of(base, reachable_betas[-1]) if reachable_betas else stable_ref
    since_previous = log_since(previous)
    if override == "auto" and not any(c.bump for c in since_previous):
        return skip  # only docs and chores since the last beta
    n = max(existing, default=0) + 1
    return Plan(True, f"{vs(base)}-beta.{n}", vs(base), tag_of(base, n), channel, previous, code, since_previous)


def notes(commits, previous, tag) -> str:
    out = []
    for heading, kinds in SECTIONS:
        if kinds is None:
            items = [c for c in commits if c.breaking]
        else:
            items = [c for c in commits if not c.breaking and c.kind in kinds]
        if items:
            out.append(f"### {heading}")
            out += [f"- {c.text[0].upper()}{c.text[1:]} ([`{c.sha[:7]}`]({REPO_URL}/commit/{c.sha}))" for c in items]
            out.append("")
    if not out:
        out = ["No user-facing changes.", ""]
    out.append(f"**Full changes:** [{previous}...{tag}]({REPO_URL}/compare/{previous}...{tag})")
    return "\n".join(out) + "\n"


def changelog(existing, version, body, date) -> str:
    """Puts a section for [version] above the newest one. A version already listed is left alone."""
    if f"## [{version}]" in existing:
        return existing
    section = f"## [{version}] - {date}\n\n{body.rstrip()}\n\n"
    i = existing.find("\n## ")
    if i < 0:
        return existing.rstrip() + "\n\n" + section.rstrip() + "\n"
    return existing[: i + 1] + section + existing[i + 1:]


# ---- git ----

def git(*args) -> str:
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True).stdout


def git_log_since(ref) -> list:
    out = git("log", "--no-merges", "--format=%H%x1f%s%x1f%b%x1e", f"{ref}..HEAD")
    commits = []
    for rec in out.split("\x1e"):
        if rec.strip():
            sha, subject, body = (rec.strip("\n").split("\x1f") + ["", ""])[:3]
            if not sha.strip().startswith(IGNORED):
                commits.append(parse_commit(sha.strip(), subject, body))
    return commits


def git_reachable(tag) -> bool:
    return subprocess.run(["git", "merge-base", "--is-ancestor", tag, "HEAD"], capture_output=True).returncode == 0


def current_plan(channel, override) -> Plan:
    tags = parse_tags(git("tag", "--list", "v*").split())
    head = [t for t in git("tag", "--points-at", "HEAD").split() if t.startswith("v")]
    return make_plan(tags, git_log_since, git_reachable, head, channel, override, int(git("rev-list", "--count", "HEAD")))


def main(argv=None) -> int:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("mode", choices=["plan", "notes", "changelog"])
    p.add_argument("--channel", choices=["beta", "stable"], default="beta")
    p.add_argument("--bump", choices=["auto", "patch", "minor", "major"], default="auto")
    p.add_argument("--github-output", action="store_true", help="append the plan to $GITHUB_OUTPUT")
    p.add_argument("--out", help="write notes here instead of printing them")
    p.add_argument("--version")
    p.add_argument("--notes", help="notes file for the changelog mode")
    p.add_argument("--file", default="CHANGELOG.md")
    p.add_argument("--dry-run", action="store_true")
    a = p.parse_args(argv)

    if a.mode == "changelog":
        if not a.version or not a.notes:
            p.error("changelog needs --version and --notes")
        existing = open(a.file, encoding="utf-8").read() if os.path.exists(a.file) else "# Changelog\n"
        text = changelog(existing, a.version, open(a.notes, encoding="utf-8").read(), datetime.date.today().isoformat())
        if a.dry_run:
            print(text)
        else:
            open(a.file, "w", encoding="utf-8").write(text)
        return 0

    plan = current_plan(a.channel, a.bump)
    if a.mode == "plan":
        lines = {"release": str(plan.release).lower(), "version": plan.version, "base": plan.base, "code": plan.code,
                 "channel": plan.channel, "tag": plan.tag, "previous": plan.previous}
        text = "".join(f"{k}={v}\n" for k, v in lines.items())
        print(text, end="")
        if a.github_output and os.environ.get("GITHUB_OUTPUT"):
            open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8").write(text)
        return 0
    if not plan.release:
        print("Nothing to release.", file=sys.stderr)
        return 1
    body = notes(plan.commits, plan.previous, plan.tag)
    if a.out and not a.dry_run:
        open(a.out, "w", encoding="utf-8").write(body)
    else:
        print(body, end="")
    return 0


if __name__ == "__main__":
    sys.exit(main())
