pipeline {
  agent any
  options {
    timestamps()
    buildDiscarder(logRotator(numToKeepStr: '20'))
    skipDefaultCheckout(true)
  }

  environment {
    GRADLE_USER_HOME = "${WORKSPACE}/.gradle-cache"
    GITHUB_SERVER = 'https://github.sydney.edu.au/api/v3'
    GH_HOST = 'github.sydney.edu.au'
    REPO_SLUG = 'SOFT2412-COMP9412-2025s2/A3-T28-G03'
    RELEASE_TEST_MODE = 'true'
    GIT_AUTHOR_NAME = 'xfan0282'
    GIT_AUTHOR_EMAIL = 'xfan0282@uni.sydney.edu.au'
  }

  stages {
    stage('Checkout') {
      steps {
        echo "Checking out branch: ${env.BRANCH_NAME}"
        deleteDir()
        checkout scm
      }
    }

    stage('Sanity') {
      steps {
        sh '''
          echo "== PWD ==" && pwd
          echo "== Root listing ==" && ls -la
          echo "== Wrapper listing ==" && ls -la gradlew gradle/wrapper || true
          echo "== GRADLE_USER_HOME ==" && echo "$GRADLE_USER_HOME"
        '''
      }
    }

    stage('Prepare Gradle cache') {
      steps {
        sh '''
          set -e
          mkdir -p "$GRADLE_USER_HOME"
          chmod -R u+rwX "$GRADLE_USER_HOME" || true
        '''
      }
    }

    stage('Format (verify)') {
      steps {
        echo 'Running google-java-format verify...'
        script {
          def result = sh(script: './gradlew --no-daemon -g "$GRADLE_USER_HOME" verifyGoogleJavaFormat', returnStatus: true)
          if (result != 0) {
            echo '=( Code format check failed. Please run ./gradlew googleJavaFormat locally.'
          } else {
            echo '=) Code format check passed.'
          }
        }
      }
    }

    stage('Build') {
      steps {
        echo 'Building Gradle project...'
        sh '''
          chmod +x gradlew || true
          ./gradlew --no-daemon -g "$GRADLE_USER_HOME" clean assemble
        '''
      }
    }

    stage('Test & Coverage') {
      steps {
        echo 'Running unit tests and generating JaCoCo XML/HTML reports...'
        sh './gradlew --no-daemon -g "$GRADLE_USER_HOME" test jacocoTestReport'
      }
      post {
        always {
          junit allowEmptyResults: true, testResults: '**/build/test-results/test/*.xml'
          recordCoverage(
            tools: [[parser: 'JACOCO', pattern: '**/build/reports/jacoco/test/jacocoTestReport.xml']],
            sourceCodeRetention: 'LAST_BUILD',
            sourceDirectories: [[path: 'app/src/main/java'], [path: 'app/src/test/java']],
            qualityGates: [
              [metric: 'LINE',   threshold: 60.0, baseline: 'PROJECT'],
              [metric: 'BRANCH', threshold: 60.0, baseline: 'PROJECT']
            ]
          )
          publishHTML(target: [
            reportDir: 'app/build/reports/jacoco/test/html',
            reportFiles: 'index.html',
            reportName: 'JaCoCo HTML',
            keepAll: true,
            allowMissing: true
          ])
          archiveArtifacts artifacts: '**/build/reports/jacoco/test/**', allowEmptyArchive: true
        }
      }
    }

    stage('Archive') {
      steps {
        archiveArtifacts artifacts: '**/build/libs/*.jar', fingerprint: true, onlyIfSuccessful: true
      }
    }

    stage('Deploy') {
      when { branch 'main' }
      steps {
        echo 'Deploying application...'
        sh 'echo "Deploy step placeholder. Add real deployment here if needed."'
      }
    }

    stage('Release (tag + GitHub release via gh)') {
      when {
        anyOf {
          branch 'main'
          expression { return env.RELEASE_TEST_MODE == 'true' }
        }
      }
      steps {
        withCredentials([
          usernamePassword(credentialsId: 'ghe_https_pat', usernameVariable: 'GIT_USER', passwordVariable: 'GIT_TOKEN'),
          string(credentialsId: 'ghe_pat_secret', variable: 'GITHUB_PAT')
        ]) {
          sh '''
            set -euo pipefail

            # Ensure gh is installed
            if ! command -v gh >/dev/null 2>&1; then
              echo "[INFO] gh not found, installing..."
              apt-get update
              apt-get install -y curl ca-certificates gnupg >/dev/null
              curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg \
                | dd of=/usr/share/keyrings/githubcli-archive-keyring.gpg
              chmod go+r /usr/share/keyrings/githubcli-archive-keyring.gpg
              echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" \
                > /etc/apt/sources.list.d/github-cli.list
              apt-get update
              apt-get install -y gh
            fi

            # Configure git user for annotated tags
            git config user.name  "${GIT_AUTHOR_NAME}"
            git config user.email "${GIT_AUTHOR_EMAIL}"

            # Use HTTPS with PAT for pushing tags
            git remote set-url origin "https://$GIT_USER:$GIT_TOKEN@github.sydney.edu.au/${REPO_SLUG}.git"
            git fetch --tags --prune
            [ -e .git/shallow ] && git fetch --unshallow || true

            # Determine major version
            if [ ! -f VERSION_MAJOR ]; then echo "1" > VERSION_MAJOR; fi
            MAJOR=$(tr -d "\\n\\r" < VERSION_MAJOR)
            case "$MAJOR" in ''|*[!0-9]*) echo "Invalid VERSION_MAJOR: $MAJOR" >&2; exit 2 ;; esac

            # Previous tag and prefix
            LAST_TAG=$(git describe --tags --abbrev=0 2>/dev/null || true)
            if [ -z "$LAST_TAG" ]; then
              MINOR=0; PATCH=0; TAG_PREFIX="v"; RANGE_OPT=""
            else
              VT=$(printf "%s" "$LAST_TAG" | sed -E 's/^[^0-9]*([0-9].*)/\\1/')
              LT_MAJOR=$(printf "%s" "$VT" | cut -d. -f1)
              LT_MINOR=$(printf "%s" "$VT" | cut -d. -f2)
              LT_PATCH=$(printf "%s" "$VT" | cut -d. -f3)
              if [ "$LT_MAJOR" != "$MAJOR" ]; then MINOR=0; PATCH=0; else MINOR=$LT_MINOR; PATCH=$LT_PATCH; fi
              echo "$LAST_TAG" | grep -q '^[V]' && TAG_PREFIX="V" || TAG_PREFIX="v"
              RANGE_OPT="$LAST_TAG..HEAD"
            fi

            HEAD_SHA=$(git rev-parse HEAD)

            # Derive source branch via API first, fallback to merge subject
            SRC_BRANCH=""
            for DELAY in 1 2 4; do
              RESP=$(curl -sS -H "Authorization: token $GITHUB_PAT" -H "Accept: application/vnd.github+json" \
                "$GITHUB_SERVER/repos/$REPO_SLUG/commits/$HEAD_SHA/pulls" || true)
              SRC_BRANCH=$(printf "%s" "$RESP" | sed -n 's/.*"head":{[^}]*"ref":"\\([^"]*\\)".*/\\1/p' | head -n1)
              [ -n "$SRC_BRANCH" ] && break
              sleep "$DELAY"
            done
            if [ -z "$SRC_BRANCH" ]; then
              MERGE_SUBJ=$(git log -1 --pretty=%s || true)
              if printf "%s" "$MERGE_SUBJ" | grep -Eiq '^Merge pull request #[0-9]+'; then
                SRC_BRANCH=$(printf "%s" "$MERGE_SUBJ" | sed -n 's#.* from [^/]*/\\([^ )]*\\).*#\\1#p')
              fi
            fi

            # Bump rule: feature/feat -> minor++; else patch++
            if [ -z "$SRC_BRANCH" ]; then
              echo "WARN: cannot determine PR source branch; default to patch bump"
              IS_FEAT=0
            else
              echo "Detected source branch: $SRC_BRANCH"
              echo "$SRC_BRANCH" | grep -Eiq '^(feat|feature)/' && IS_FEAT=1 || IS_FEAT=0
            fi
            if [ "$IS_FEAT" = 1 ]; then
              MINOR=$((MINOR+1)); PATCH=0
            else
              PATCH=$((PATCH+1))
            fi

            NEXT_VERSION="${TAG_PREFIX}${MAJOR}.${MINOR}.${PATCH}"
            echo "$NEXT_VERSION" | tee next-version.txt

            # Tag if not exists
            if git rev-parse -q --verify "refs/tags/$NEXT_VERSION" >/dev/null; then
              echo "Tag $NEXT_VERSION already exists, skip tagging."
            else
              git tag -a "$NEXT_VERSION" -m "Release $NEXT_VERSION (automated by Jenkins)"
              git push origin "$NEXT_VERSION"
            fi

            # Prepare changelog text file
            if [ -n "$RANGE_OPT" ]; then
              git log --no-merges --pretty="* %h %s (%an)" $RANGE_OPT > CHANGELOG.txt
            else
              git log -1 --pretty="* %h %s (%an)" > CHANGELOG.txt
            fi

            # gh auth (GHES)
            export GH_TOKEN="$GITHUB_PAT"
            echo "$GITHUB_PAT" | gh auth login --hostname "$GH_HOST" --with-token

            # Create or update release via gh
            if gh release view "$NEXT_VERSION" --repo "$REPO_SLUG" --hostname "$GH_HOST" >/dev/null 2>&1; then
              gh release edit "$NEXT_VERSION" \
                --repo "$REPO_SLUG" \
                --hostname "$GH_HOST" \
                --title "$NEXT_VERSION" \
                --notes-file CHANGELOG.txt
            else
              gh release create "$NEXT_VERSION" \
                --repo "$REPO_SLUG" \
                --hostname "$GH_HOST" \
                --target "$HEAD_SHA" \
                --title "$NEXT_VERSION" \
                --notes-file CHANGELOG.txt
            fi

            # Optional: upload build artifacts as release assets
            if ls app/build/distributions/* >/dev/null 2>&1; then
              gh release upload "$NEXT_VERSION" app/build/distributions/* \
                --repo "$REPO_SLUG" --hostname "$GH_HOST" --clobber || true
            fi
          '''
          archiveArtifacts artifacts: 'next-version.txt', fingerprint: true, allowEmptyArchive: false
        }
      }
    }
  }

  post {
    success { echo 'Pipeline completed successfully.' }
    failure { echo 'Pipeline failed.' }
  }
}
