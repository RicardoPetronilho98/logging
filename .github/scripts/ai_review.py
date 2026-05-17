"""
Posts an AI-powered code review as a PR comment using Claude + project CLAUDE.md context.

Required env vars:
  ANTHROPIC_API_KEY  — Anthropic API key (repository secret)
  GITHUB_TOKEN       — injected automatically by GitHub Actions
  PR_NUMBER          — PR number
  REPO               — owner/repo (e.g. acme/logging)
  BASE_SHA           — base commit SHA of the PR
  HEAD_SHA           — head commit SHA of the PR
"""

import os
import subprocess
import sys
import anthropic
import requests

MODEL = "claude-sonnet-4-6"
MAX_DIFF_CHARS = 120_000
REVIEW_COMMENT_MARKER = "<!-- ai-code-review-bot -->"


def get_diff(base_sha: str, head_sha: str) -> str:
    result = subprocess.run(
        ["git", "diff", f"{base_sha}...{head_sha}"],
        capture_output=True,
        text=True,
        check=True,
    )
    return result.stdout


def collect_claude_md() -> str:
    result = subprocess.run(
        ["find", ".", "-name", "CLAUDE.md", "-not", "-path", "*/target/*"],
        capture_output=True,
        text=True,
        check=True,
    )
    parts = []
    for path in sorted(result.stdout.strip().splitlines()):
        if not path:
            continue
        try:
            with open(path) as f:
                parts.append(f"### {path}\n\n{f.read()}")
        except OSError:
            pass
    return "\n\n---\n\n".join(parts)


def find_existing_review_comment(repo: str, pr_number: str, token: str) -> int | None:
    url = f"https://api.github.com/repos/{repo}/issues/{pr_number}/comments?per_page=100"
    headers = {"Authorization": f"Bearer {token}", "Accept": "application/vnd.github.v3+json"}
    response = requests.get(url, headers=headers, timeout=30)
    response.raise_for_status()
    for comment in response.json():
        if REVIEW_COMMENT_MARKER in comment.get("body", ""):
            return comment["id"]
    return None


def post_or_update_comment(repo: str, pr_number: str, token: str, body: str) -> None:
    headers = {"Authorization": f"Bearer {token}", "Accept": "application/vnd.github.v3+json"}
    existing_id = find_existing_review_comment(repo, pr_number, token)

    if existing_id:
        url = f"https://api.github.com/repos/{repo}/issues/comments/{existing_id}"
        response = requests.patch(url, json={"body": body}, headers=headers, timeout=30)
    else:
        url = f"https://api.github.com/repos/{repo}/issues/{pr_number}/comments"
        response = requests.post(url, json={"body": body}, headers=headers, timeout=30)

    response.raise_for_status()


def build_review(diff: str, claude_md: str, truncated: bool) -> str:
    client = anthropic.Anthropic()

    system_prompt = f"""\
You are an expert code reviewer for the logging library (com.playground.logging).
Your job is to review pull request diffs against the project's architecture, patterns, and constraints.

## Project documentation (CLAUDE.md files)

{claude_md}
"""

    user_prompt = f"""\
Review the following pull request diff. Apply the project context from CLAUDE.md strictly.

Focus on:
1. **Correctness** — logic errors, edge cases, null handling
2. **Architecture** — dependency direction violations, layer concerns leaking across modules
3. **Domain Constraints** — field validations, business rules (unique name per user, max chars, UUID format)
4. **Patterns** — MapStruct mapper config, use-case Input/Output structure, MongoTemplate usage, dual-ID strategy
5. **Documentation** — per the CLAUDE.md maintenance table, are the right docs updated for these changes?
6. **Security** — input validation at system boundaries, no sensitive data exposure
7. **Code Quality** — unnecessary complexity, missing error handling at boundaries

## Format

Start with a **Summary** (2–3 sentences).
Then list findings grouped by severity — use exactly these labels:

🔴 **Critical** — must fix before merge
🟡 **Warning** — should fix
🔵 **Suggestion** — optional improvement

For each finding include: file + line range, what the issue is, and a concrete fix.
If there are no issues, say so explicitly.

## Diff

```diff
{diff}
```
"""

    message = client.messages.create(
        model=MODEL,
        max_tokens=4096,
        system=system_prompt,
        messages=[{"role": "user", "content": user_prompt}],
    )

    review_text = message.content[0].text
    truncation_notice = (
        f"> ⚠️ Diff was truncated to {MAX_DIFF_CHARS:,} characters — large files may be partially reviewed.\n\n"
        if truncated
        else ""
    )

    return (
        f"{REVIEW_COMMENT_MARKER}\n"
        f"## AI Code Review\n\n"
        f"{truncation_notice}"
        f"{review_text}\n\n"
        f"---\n"
        f"*Powered by [{MODEL}](https://www.anthropic.com) · "
        f"[workflow](.github/workflows/ai-code-review.yml)*"
    )


def main() -> None:
    base_sha = os.environ["BASE_SHA"]
    head_sha = os.environ["HEAD_SHA"]
    pr_number = os.environ["PR_NUMBER"]
    repo = os.environ["REPO"]
    token = os.environ["GITHUB_TOKEN"]

    diff = get_diff(base_sha, head_sha)
    if not diff.strip():
        print("No diff found — skipping review.")
        return

    truncated = len(diff) > MAX_DIFF_CHARS
    if truncated:
        diff = diff[:MAX_DIFF_CHARS] + "\n\n[... diff truncated ...]"

    claude_md = collect_claude_md()
    body = build_review(diff, claude_md, truncated)
    post_or_update_comment(repo, pr_number, token, body)
    print("Review posted successfully.")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        sys.exit(1)
