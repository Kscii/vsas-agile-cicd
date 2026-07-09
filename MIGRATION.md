# Migration Notes

This repository is a public portfolio migration of the University of Sydney GHES repository:

`git@github.sydney.edu.au:SOFT2412-COMP9412-2025s2/A3-T28-G03.git`

The preferred GitHub Enterprise Importer path could not be completed because the GitHub.com account did not have an available organization to use as the required GEI migration target. The migration therefore used the fallback path: direct Git mirror migration to `Kscii/vsas-agile-cicd`, followed by release reconstruction from the source API.

## Migrated

- All normal Git branches were mirrored to GitHub.com.
- All annotated tags were mirrored to GitHub.com.
- Commit history and merge commits were preserved without rewriting author identity or commit SHA values.
- 47 GitHub releases were recreated on GitHub.com.
- 25 release assets were copied from the source GHES releases to the recreated GitHub.com releases.
- The Jenkins CI/CD pipeline definition is preserved in `Jenkinsfile`.

## Not Recreated As Native GitHub Objects

- Pull request pages, review timelines, and issue pages were not recreated as native GitHub.com PRs/issues in the fallback path.
- The source repository exposed 56 pull requests through the API. Their merge evidence remains visible in preserved merge commits such as `Merge pull request #107 ...`.
- GitHub Projects were not migrated in the fallback path.

## Validation Snapshot

- Source GHES version observed: `3.18.10`.
- Source repository: `SOFT2412-COMP9412-2025s2/A3-T28-G03`.
- Target repository: `Kscii/vsas-agile-cicd`.
- Target visibility: public.
- Main branch HEAD after mirror: `d9bb45932827ea23c5ef32e9557776e61a4d7589`.
- Mirrored normal branches: 56.
- Mirrored tags: 47.
- Recreated releases: 47.
- Copied release assets: 25.

Author history was intentionally not rewritten during this migration. A future history-rewrite pass can remap selected commit authors, but that would change commit SHAs and reduce fidelity against the original PR/release graph.
