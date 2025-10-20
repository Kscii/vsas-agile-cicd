pipeline {
  agent any
  options {
    timestamps()
    buildDiscarder(logRotator(numToKeepStr: '20'))
  }

  stages {
    stage('Checkout') {
      steps {
        echo "Checking out branch: ${env.BRANCH_NAME}"
        checkout scm
      }
    }

    stage('Sanity') {
      steps {
        sh '''
          echo "== PWD ==" && pwd
          echo "== Root listing ==" && ls -la
          echo "== Wrapper listing ==" && ls -la gradlew gradle/wrapper || true
        '''
      }
    }

    stage('Format (verify)') {
      steps {
        echo 'Running google-java-format verify...'
        script {
          def result = sh(script: './gradlew verifyGoogleJavaFormat --no-daemon', returnStatus: true)
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
          ./gradlew clean assemble --no-daemon
        '''
      }
    }

    stage('Test & Coverage') {
      steps {
        echo 'Running unit tests and generating JaCoCo XML/HTML reports...'
        sh './gradlew test jacocoTestReport --no-daemon'
      }
      post {
        always {
          junit allowEmptyResults: true, testResults: '**/build/test-results/test/*.xml'
          recordCoverage(
            tools: [[parser: 'JACOCO', pattern: '**/build/reports/jacoco/test/jacocoTestReport.xml']],
            sourceCodeRetention: 'LAST_BUILD',
            qualityGates: [
              [metric: 'LINE',   threshold: 60.0, baseline: 'PROJECT', unstable: true],
              [metric: 'BRANCH', threshold: 60.0, baseline: 'PROJECT', unstable: true]
            ]
          )
          publishHTML(target: [
            reportDir: 'build/reports/jacoco/test/html',
            reportFiles: 'index.html',
            reportName: 'JaCoCo HTML',
            keepAll: true,
            allowMissing: true
          ])
          archiveArtifacts artifacts: '**/build/reports/jacoco/test/**', allowEmptyArchive: true
        }
      }
    }

    // ===================== CD (Auto Tag & Release) =====================
    stage('Release (CD)') {
      when { branch 'main' }
      environment {
        GITHUB_TOKEN = credentials('github_pat')
      }
      steps {
        sh '''
          set -euo pipefail

          echo "== CD start =="

          #--------- helper: jq (download if missing) ----------
          if ! command -v jq >/dev/null 2>&1; then
            echo "jq not found; downloading static jq..."
            # linux x86_64 static jq；如为 arm64 可替换下载地址
            curl -sSL -o jq https://github.com/stedolan/jq/releases/download/jq-1.6/jq-linux64
            chmod +x jq
            JQ="./jq"
          else
            JQ="jq"
          fi

          #--------- detect repo owner/name ----------
          REPO_URL="$(git config --get remote.origin.url || true)"
          if [ -z "$REPO_URL" ]; then
            echo "Cannot determine remote.origin.url"; exit 1
          fi
          # supports both https and ssh urls
          REPO_SLUG="$(echo "$REPO_URL" | sed -E 's#(git@github.com:|https://github.com/)##; s#\\.git$##')"
          OWNER="$(echo "$REPO_SLUG" | cut -d/ -f1)"
          REPO="$(echo "$REPO_SLUG" | cut -d/ -f2)"
          API="https://api.github.com/repos/${OWNER}/${REPO}"

          echo "Repository: $OWNER/$REPO"

          #--------- prep: tags & major ----------
          git fetch --tags --prune --force

          if [ -f ".version-major" ]; then
            MAJOR="$(tr -d "[:space:]" < .version-major)"
          else
            MAJOR="1"
            echo "Warn: .version-major not found; default MAJOR=$MAJOR"
          fi

          LAST_TAG="$(git describe --tags --abbrev=0 --match 'v[0-9]*.[0-9]*.[0-9]*' 2>/dev/null || true)"
          if [ -z "$LAST_TAG" ]; then
            echo "No previous tag. Process all commits on main."
            COMMITS=$(git rev-list --no-merges --reverse HEAD)
            MINOR=0; PATCH=0
          else
            echo "Last tag: $LAST_TAG"
            COMMITS=$(git rev-list --no-merges --reverse "${LAST_TAG}..HEAD")
            LAST_MAJOR="$(echo "$LAST_TAG" | sed -E 's/^v([0-9]+)\\..*$/\\1/')"
            LAST_MINOR="$(echo "$LAST_TAG" | sed -E 's/^v[0-9]+\\.([0-9]+)\\..*$/\\1/')"
            LAST_PATCH="$(echo "$LAST_TAG" | sed -E 's/^v[0-9]+\\.[0-9]+\\.([0-9]+)$/\\1/')"
            if [ "$LAST_MAJOR" != "$MAJOR" ]; then
              MINOR=0; PATCH=0
            else
              MINOR="$LAST_MINOR"; PATCH="$LAST_PATCH"
            fi
          fi

          if [ -z "${COMMITS:-}" ]; then
            echo "No new commits since last tag. Nothing to release."
            exit 0
          fi

          #--------- iterate commits (backlog-safe) ----------
          for SHA in $COMMITS; do
            # find associated PR for this commit
            PULLS_JSON="$(curl -sS -H "Authorization: token ${GITHUB_TOKEN}" -H "Accept: application/vnd.github+json" \
              "${API}/commits/${SHA}/pulls")"

            HEAD_REF="$(echo "$PULLS_JSON" | $JQ -r '.[0].head.ref // empty')"
            PR_NUM="$(echo  "$PULLS_JSON" | $JQ -r '.[0].number // empty')"

            if [ -z "$HEAD_REF" ]; then
              echo "[skip] commit ${SHA} has no associated PR"
              continue
            fi

            # bump rule
            shopt -s nocasematch
            if [[ "$HEAD_REF" =~ ^feat|^feature ]]; then
              MINOR=$((MINOR+1)); PATCH=0; BUMP="minor"
            else
              PATCH=$((PATCH+1)); BUMP="patch"
            fi
            shopt -u nocasematch

            VERSION="v${MAJOR}.${MINOR}.${PATCH}"

            # idempotency: skip if tag/release exists
            if git rev-parse -q --verify "refs/tags/${VERSION}" >/dev/null; then
              echo "[skip] tag ${VERSION} exists"
              continue
            fi
            HTTP_CODE=$(curl -sS -o /dev/null -w "%{http_code}" -H "Authorization: token ${GITHUB_TOKEN}" \
              "${API}/releases/tags/${VERSION}" || true)
            if [ "$HTTP_CODE" = "200" ]; then
              echo "[skip] release ${VERSION} exists"
              continue
            fi

            echo "Creating release ${VERSION} (commit ${SHA}, PR #${PR_NUM:-N/A}, branch ${HEAD_REF}, bump ${BUMP})"

            BODY=$(cat <<EOF
Auto release ${VERSION}
- Commit: ${SHA}
- PR: #${PR_NUM:-N/A}
- Source branch: ${HEAD_REF}
- Bump: ${BUMP}
EOF
)

            # Create release (also creates tag if not exist)
            curl -sS -X POST -H "Authorization: token ${GITHUB_TOKEN}" -H "Accept: application/vnd.github+json" \
              "${API}/releases" \
              -d "$(printf '{"tag_name":"%s","target_commitish":"%s","name":"%s","body":%s,"draft":false,"prerelease":false}' \
                    "${VERSION}" "${SHA}" "${VERSION}" "$(printf '%s' "$BODY" | $JQ -R -s '.')")" \
              >/dev/null

          done

          echo "== CD done =="
        '''
      }
    }
    // ===================== End CD =====================
  }

  post {
    success { echo 'Pipeline completed successfully.' }
    failure { echo 'Pipeline failed.' }
  }
}
