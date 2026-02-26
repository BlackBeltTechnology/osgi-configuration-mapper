# Development Versioning and Branch Handling

## Branch Strategy

This project follows a GitFlow-based branching model. Each branch type has a specific purpose and naming convention.

```mermaid
gitGraph
    commit id: "initial"
    branch develop
    checkout develop
    commit id: "dev-1"
    branch feature/JNG-1
    checkout feature/JNG-1
    commit id: "feat-1"
    commit id: "feat-2"
    checkout develop
    merge feature/JNG-1 id: "merge-feat-1"
    branch feature/JNG-3
    checkout feature/JNG-3
    commit id: "feat-3"
    checkout develop
    merge feature/JNG-3 id: "merge-feat-3"
    branch release/1.0-beta1
    checkout release/1.0-beta1
    commit id: "rc-1"
    branch bugfix/JNG-4
    checkout bugfix/JNG-4
    commit id: "bugfix"
    checkout release/1.0-beta1
    merge bugfix/JNG-4 id: "merge-bugfix"
    checkout develop
    merge release/1.0-beta1 id: "merge-release"
    checkout main
    merge release/1.0-beta1 id: "release-1.0"
```

### Branch Types

| Branch | Base | Purpose |
|--------|------|---------|
| `develop` | — | Main development branch with latest sources of the active version |
| `feature/JNG-NUMBER_short_summary` | `develop` | New features for the next version |
| `(release/)X.Y.Z` | `develop` | Release stabilization branches (the `release/` prefix is reserved for CI) |
| `bugfix/JNG-NUMBER_short_summary` | release branch | Fixes applied during release testing; must be merged to both release and newer develop branches |
| `support/JNG-NUMBER_short_summary` | release branch | Minor changes for a previous release; merged back to the release branch |
| `master` | — | Latest released sources of the active version |
| `hotfix/JNG-NUMBER_short_summary` | `master` | Urgent fixes applied to both release and master branches |

## Version Numbers

Versioning follows semantic versioning with these rules:

- **Feature branches** — do not change version numbers
- **Develop branch** — increment the 2nd number when a release branch is started
- **Bugfix branches** — do not change version numbers (applied to release branches before merging to master)
- **Support branches** — increment the 3rd number when started; merged back to the release branch without merging to master
- **Hotfix branches** — increment the 4th number when started; applied to both release and master branches

## GitHub Actions CI/CD Workflows

The project uses several interconnected GitHub Actions workflows.

### build.yml — Main Build Pipeline

```mermaid
flowchart TD
    trigger["Push on develop<br/>or PR on develop/master/increment/release"]
    trigger --> check{Branch type?}
    check -->|"master, release/*"| version1["Set version from pom.xml<br/>(without -SNAPSHOT)"]
    check -->|"develop, increment/*"| version2["Set version<br/>major.minor.qualifier.date_commitId_branch"]
    version1 --> build[Build and deploy to Nexus]
    version2 --> build
    build --> tag["Create git tag v&lt;version&gt;"]
    tag --> check2{Branch type?}
    check2 -->|"increment/*, release/*"| mergeTag["Create tag merge-pr/&lt;version&gt;"]
    mergeTag --> triggerMerge["Trigger merge-pr-tagged.yml"]
    check2 -->|develop| changelog["Build changelog"]
    changelog --> release["Create GitHub pre-release"]
```

### merge-pr-tagged.yml — PR Merge Automation

```mermaid
flowchart TD
    trigger["Push on merge-pr/* tag"]
    trigger --> getVersion["Get version from tag name"]
    getVersion --> check{Version format?}
    check -->|"major.minor.qualifier"| merge["Merge PR to master"]
    merge --> triggerRelease["Trigger create-release-on-master.yml"]
    check -->|other| squash["Squash PR to develop"]
    squash --> triggerBuild["Trigger build.yml"]
    merge --> cleanup["Delete merge-pr/* tag"]
    squash --> cleanup
```

### create-release-on-master.yml — Release Creation

```mermaid
flowchart TD
    trigger["Push on master branch"]
    trigger --> version["Get version from tag"]
    version --> changelog["Build changelog"]
    changelog --> release["Create GitHub release (latest)"]
```

### release.yml — Manual Release

```mermaid
flowchart TD
    trigger["Manual trigger with version input"]
    trigger --> check{Given version?}
    check -->|"'auto'"| auto["Use pom.xml version<br/>(without -SNAPSHOT)"]
    check -->|specific| specific["Use given version"]
    auto --> next["Set next version = qualifier + 1"]
    specific --> next
    next --> prMaster["Create PR on master<br/>with release version"]
    next --> prDevelop["Create PR on develop<br/>with next version"]
    prMaster --> buildMaster["Trigger build.yml"]
    prDevelop --> buildDevelop["Trigger build.yml"]
```

## Development Rules

> **Important:** There is no commit without a ticket number. Every pull request and commit must reference a JIRA ticket in the format `JNG-xxx`.

Issue tracking is done via [JIRA](https://blackbelt.atlassian.net/jira/dashboards).
