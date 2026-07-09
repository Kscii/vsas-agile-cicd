# Migration Notes

This repository is a public portfolio migration of the University of Sydney GitHub Enterprise Server repository:

```text
git@github.sydney.edu.au:SOFT2412-COMP9412-2025s2/A3-T28-G03.git
```

Target repository:

```text
https://github.com/Kscii/vsas-agile-cicd
```

## Migration Path

The preferred GitHub Enterprise Importer path could not be completed because a GitHub.com organization was not available as the required migration target. The repository therefore used the fallback path:

1. mirror all Git branches and annotated tags to GitHub.com;
2. reconstruct GitHub releases and release assets from the source API;
3. perform a second history rewrite on GitHub.com to normalize selected author identities for portfolio attribution.

The Sydney source repository was not modified.

## Author Rewrite

After the initial mirror migration, Git history on GitHub.com was rewritten to map the author's historical Sydney identities to:

```text
kscii <xuejian-fang@outlook.com>
```

Mapped identities:

- `xfan0282 <xfan0282@uni.sydney.edu.au>`
- `Xuejian Fang <xfan0282@uni.sydney.edu.au>`
- planned aliases `xuejianfang` and `xan0282` if present in history

The rewrite covered branch commit authors, matching committers, and annotated taggers through `git-filter-repo --mailmap`. This changed commit and tag object SHAs, while preserving branch names, tag names, release names, and release assets.

Rewritten `main` HEAD before the portfolio README branch:

```text
13cdbef2d6c36bc2e2e93c33ea0f2f9eb22a4ca5
```

## Migrated

- All normal Git branches were mirrored to GitHub.com.
- All annotated tags were mirrored and then rewritten on GitHub.com.
- Merge commits were preserved as commits, including messages such as `Merge pull request #107 ...`.
- 47 GitHub releases were recreated on GitHub.com.
- 25 release assets were copied from the source GHES releases.
- The Jenkins CI/CD pipeline definition is preserved in `Jenkinsfile`.
- Sprint report PDFs are archived under `docs/sprints/`.

## Not Recreated As Native GitHub Objects

- Pull request pages, review timelines, and issue pages were not recreated as native GitHub.com PRs/issues in the fallback path.
- The source repository exposed 56 pull requests through the API. Their merge evidence remains visible in preserved merge commits, but native PR discussions remain in the source GHES system.
- GitHub Projects were not migrated in the fallback path.

## Branch Policy

Historical branches were preserved for evidence, including:

- `Appendix_A`
- `ci/jenkins-cd-auto-release`
- `feat/us-d2-download-scroll`

These were not merged into `main` after migration because they represent old branch states. Some would remove later functionality or add large root-level binary evidence files if merged directly.

## Validation Snapshot

- Source GHES version observed: `3.18.10`.
- Source repository: `SOFT2412-COMP9412-2025s2/A3-T28-G03`.
- Target repository: `Kscii/vsas-agile-cicd`.
- Target visibility: public.
- Rewritten normal branches: 56.
- Rewritten annotated tags: 47.
- Recreated releases: 47.
- Copied release assets: 25.
- Latest migrated release: `V3.8.3`.
