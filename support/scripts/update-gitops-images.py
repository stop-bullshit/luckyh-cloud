"""Update the six backend image tags after every image has been published."""

import argparse
from pathlib import Path
import re


SERVICES = ("account", "auth", "gateway", "inventory", "order", "user")
IMAGE = re.compile(
    r"(?m)^([ \t]*image:[ \t]*[\"']?ghcr\.io/stop-bullshit/luckyh-"
    r"(account|auth|gateway|inventory|order|user)-service:sha-)"
    r"[0-9a-f]{40}([\"']?[ \t]*)$"
)


def update_images(manifest: str, commit_sha: str) -> str:
    if not re.fullmatch(r"[0-9a-f]{40}", commit_sha):
        raise ValueError("Expected a complete lowercase Git commit SHA.")
    matches = list(IMAGE.finditer(manifest))
    if sorted(match.group(2) for match in matches) != list(SERVICES):
        raise ValueError("Expected exactly one image for each of the six backend services.")
    return IMAGE.sub(lambda match: match.group(1) + commit_sha + match.group(3), manifest)


def self_test() -> None:
    previous_sha, next_sha = "a" * 40, "b" * 40
    manifest = "\n".join(
        f'    image: "ghcr.io/stop-bullshit/luckyh-{service}-service:sha-{previous_sha}"'
        for service in SERVICES
    ) + "\n    replicas: 2\n"
    result = update_images(manifest, next_sha)
    assert result == manifest.replace(previous_sha, next_sha)
    assert update_images(result, next_sha) == result
    for invalid_manifest, invalid_sha in ((manifest, "invalid"), ("", next_sha), (manifest + manifest, next_sha)):
        try:
            update_images(invalid_manifest, invalid_sha)
        except ValueError:
            continue
        raise AssertionError("Invalid SHA or incomplete/duplicate image set was accepted.")
    print("GitOps image updater checks passed.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("commit_sha", nargs="?")
    parser.add_argument("--self-test", action="store_true")
    arguments = parser.parse_args()
    if arguments.self_test:
        self_test()
    else:
        if not arguments.commit_sha:
            parser.error("commit_sha is required unless --self-test is used")
        path = Path(__file__).resolve().parents[1] / "gitops/backend/applications.yaml"
        updated = update_images(path.read_text(encoding="utf-8"), arguments.commit_sha)
        path.write_text(updated, encoding="utf-8", newline="\n")
