pipeline {
  agent any
  options {
    timestamps()
    buildDiscarder(logRotator(numToKeepStr: '20'))
    skipDefaultCheckout(true)
  }

  environment {
    GRADLE_USER_HOME = "${WORKSPACE}/.gradle-cache"

    // --- GitHub repo info (adjust if your origin changes) ---
    GITHUB_HOST  = 'github.sydney.edu.au'
    GITHUB_OWNER = 'SOFT2412-COMP9412-2025s2'
    GITHUB_REPO  = 'A3-T28-G03'

    // If true, allow releases on non-main branches (for testing)
    RELEASE_TEST_MODE = 'false'
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

    // ===== Release section (tag + GitHub Release via gh) =====
    stage('Release (tag + GitHub release via gh)') {
      when {
        expression {
          // allow on main; or allow on any branch if RELEASE_TEST_MODE=true
          return (env.BRANCH_NAME == 'main') || (env.RELEASE_TEST_MODE?.toBoolean())
        }
      }
      environment {
        GH_HOST = "${GITHUB_HOST}" // gh respects GH_HOST to target the enterprise host
      }
      steps {
        withCredentials([string(credentialsId: 'ghe_pat_secret', variable: 'GITHUB_PAT')]) {
          sh '''
            set -euo pipefail

            # Ensure we can fetch/push with PAT over HTTPS
            git remote set-url origin "https://${GIT_USERNAME:-${USER:-jenkins}}:${GITHUB_PAT}@${GITHUB_HOST}/${GITHUB_OWNER}/${GITHUB_REPO}.git"
            git fetch --tags --prune

            git config user.name "xfan0282"
            git config user.email "xfan0282@uni.sydney.edu.au"

            # Read MAJOR from file; fallback to 1 if missing
            if [ -f VERSION_MAJOR ]; then
              MAJOR="$(tr -d '\\n\\r' < VERSION_MAJOR)"
            else
              MAJOR="1"
            fi

            LAST_TAG="$(git describe --tags --abbrev=0 || true)"
            if [ -z "${LAST_TAG}" ]; then
              LT_MAJOR="0"; LT_MINOR="0"; LT_PATCH="0"; TAG_PREFIX="V"; RANGE_OPT=""
            else
              # Normalize like: V1.8.4 -> 1.8.4
              VT="$(printf %s "${LAST_TAG}" | sed -E 's/^[^0-9]*([0-9].*)/\\1/')"
              LT_MAJOR="$(printf %s "${VT}" | cut -d. -f1)"
              LT_MINOR="$(printf %s "${VT}" | cut -d. -f2)"
              LT_PATCH="$(printf %s "${VT}" | cut -d. -f3)"
              printf %s "${LAST_TAG}" | grep -q '^[V]' && TAG_PREFIX='V' || TAG_PREFIX='v'
              RANGE_OPT="${LAST_TAG}..HEAD"
            fi

            # If MAJOR bumped, reset minor/patch
            if [ "${LT_MAJOR}" != "${MAJOR}" ]; then
              MINOR=0
              PATCH=0
            else
              # Determine bump by PR source branch (API-first) or fallback to commit subject
              HEAD_SHA="$(git rev-parse HEAD)"
              SRC_BRANCH=""
              for i in 1 2 4; do
                RESP="$(curl -sS -H "Authorization: token ${GITHUB_PAT}" -H "Accept: application/vnd.github+json" \
                  "https://${GITHUB_HOST}/api/v3/repos/${GITHUB_OWNER}/${GITHUB_REPO}/commits/${HEAD_SHA}/pulls")" || true
                SRC_BRANCH="$(printf %s "${RESP}" | sed -n 's/.*"head":{[^}]*"ref":"\\([^"]*\\)".*/\\1/p' | head -n1)"
                [ -n "${SRC_BRANCH}" ] && break || sleep "${i}"
              done

              MERGE_SUBJ="$(git log -1 --pretty=%s)"
              if printf %s "${SRC_BRANCH:-}" | grep -Eiq '^(feat|feature)(/|-)'; then
                # feature -> minor++
                MINOR=$((LT_MINOR + 1))
                PATCH=0
              elif printf %s "${MERGE_SUBJ}" | grep -Eiq '^Merge pull request #[0-9]+' && \
                   printf %s "${MERGE_SUBJ}" | grep -Eiq '(feat|feature)'; then
                MINOR=$((LT_MINOR + 1))
                PATCH=0
              else
                # default -> patch++
                MINOR="${LT_MINOR}"
                PATCH=$((LT_PATCH + 1))
              fi
            fi

            NEXT_VERSION="${TAG_PREFIX}${MAJOR}.${MINOR}.${PATCH}"
            echo "${NEXT_VERSION}" | tee next-version.txt

            # Create annotated tag if not exists, then push
            if ! git rev-parse -q --verify "refs/tags/${NEXT_VERSION}" >/dev/null; then
              git tag -a "${NEXT_VERSION}" -m "Release ${NEXT_VERSION} (automated by Jenkins)"
              git push origin "${NEXT_VERSION}"
            fi

            # Build changelog file (only if there are commits since last tag)
            if [ -n "${RANGE_OPT}" ]; then
              {
                echo "Changes since ${LAST_TAG}:"
                echo
                git log --no-merges --pretty='* %h %s (%an)' "${RANGE_OPT}" || true
              } > CHANGELOG.txt
            else
              echo "Initial release." > CHANGELOG.txt
            fi

            # Use GitHub CLI with token via env; no interactive login needed
            export GH_TOKEN="${GITHUB_PAT}"
            export GH_HOST="${GITHUB_HOST}"

            # If release already exists, skip; else create
            if gh release view "${NEXT_VERSION}" -R "${GITHUB_OWNER}/${GITHUB_REPO}" >/dev/null 2>&1; then
              echo "Release ${NEXT_VERSION} already exists. Skipping creation."
            else
              gh release create "${NEXT_VERSION}" \
                -R "${GITHUB_OWNER}/${GITHUB_REPO}" \
                --target "${HEAD_SHA}" \
                --title "${NEXT_VERSION}" \
                --notes-file CHANGELOG.txt
              echo "Release ${NEXT_VERSION} created via gh."
            fi

            # Archive helper artifacts
            :
          '''
          archiveArtifacts artifacts: 'next-version.txt, CHANGELOG.txt', allowEmptyArchive: true
        }
      }
    }
  }

  post {
    success { echo 'Pipeline completed successfully.' }
    failure { echo 'Pipeline failed.' }
  }
}
