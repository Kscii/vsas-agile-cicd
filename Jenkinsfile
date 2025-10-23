pipeline {
  agent any

  options {
    timestamps()
    buildDiscarder(logRotator(numToKeepStr: '20'))
    skipDefaultCheckout(true)
  }

  environment {
    GRADLE_USER_HOME = "${WORKSPACE}/.gradle-cache"

    // GitHub info
    GITHUB_HOST  = 'github.sydney.edu.au'
    GITHUB_OWNER = 'SOFT2412-COMP9412-2025s2'
    GITHUB_REPO  = 'A3-T28-G03'

    // Allow release on non-main branches for testing if set to 'true'
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
            echo ':( Code format check failed. Please run ./gradlew googleJavaFormat locally.'
          } else {
            echo ':) Code format check passed.'
          }
        }
      }
    }

    stage('Build') {
      steps {
        echo 'Building Gradle project...'
        sh '''
          set -e
          chmod +x gradlew || true
          # build regular jars and the fat/uber jar
          ./gradlew --no-daemon -g "$GRADLE_USER_HOME" clean assemble shadowJar
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
          // JUnit test results
          junit allowEmptyResults: true, testResults: '**/build/test-results/test/*.xml'

          // Coverage (guard for plugin differences)
          script {
            try {
              recordCoverage(
                tools: [[parser: 'JACOCO', pattern: '**/build/reports/jacoco/test/jacocoTestReport.xml']],
                sourceCodeRetention: 'EVERY_BUILD',
                sourceDirectories: [[path: 'app/src/main/java'], [path: 'app/src/test/java']]
              )
            } catch (err) {
              echo "recordCoverage skipped (plugin mismatch or report missing): ${err}"
            }
          }

          // HTML coverage report
          publishHTML(target: [
            reportDir: 'app/build/reports/jacoco/test/html',
            reportFiles: 'index.html',
            reportName: 'JaCoCo HTML',
            keepAll: true,
            allowMissing: true
          ])

          // Keep coverage artifacts
          archiveArtifacts artifacts: '**/build/reports/jacoco/test/**', allowEmptyArchive: true
        }
      }
    }

    stage('Archive') {
      steps {
        // keep both regular and fat jars
        archiveArtifacts artifacts: 'app/build/libs/*.jar, app/build/libs/*-all.jar', fingerprint: true, onlyIfSuccessful: true
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
        expression {
          (env.BRANCH_NAME == 'main') || (env.RELEASE_TEST_MODE?.toBoolean())
        }
      }
      environment {
        GH_HOST = "${GITHUB_HOST}"
      }
      steps {
        withCredentials([string(credentialsId: 'ghe_pat_secret', variable: 'GITHUB_PAT')]) {
          sh '''
            set -euo pipefail

            # Configure remote with PAT for fetch/push
            git remote set-url origin "https://${GIT_USERNAME:-${USER:-jenkins}}:${GITHUB_PAT}@${GITHUB_HOST}/${GITHUB_OWNER}/${GITHUB_REPO}.git"
            git fetch --tags --prune

            # Git identity for annotated tags
            git config user.name "xfan0282"
            git config user.email "xfan0282@uni.sydney.edu.au"

            # Determine HEAD SHA early for gh --target
            HEAD_SHA="$(git rev-parse HEAD)"

            # Read major version
            if [ -f VERSION_MAJOR ]; then
              MAJOR="$(tr -d '\\n\\r' < VERSION_MAJOR)"
            else
              MAJOR="1"
            fi

            # Last tag info
            LAST_TAG="$(git describe --tags --abbrev=0 || true)"
            if [ -z "${LAST_TAG}" ]; then
              LT_MAJOR="0"; LT_MINOR="0"; LT_PATCH="0"; TAG_PREFIX="V"; RANGE_OPT=""
            else
              VT="$(printf %s "${LAST_TAG}" | sed -E 's/^[^0-9]*([0-9].*)/\\1/')"
              LT_MAJOR="$(printf %s "${VT}" | cut -d. -f1)"
              LT_MINOR="$(printf %s "${VT}" | cut -d. -f2)"
              LT_PATCH="$(printf %s "${VT}" | cut -d. -f3)"
              printf %s "${LAST_TAG}" | grep -q '^[V]' && TAG_PREFIX='V' || TAG_PREFIX='v'
              RANGE_OPT="${LAST_TAG}..HEAD"
            fi

            # Compute next version
            if [ "${LT_MAJOR}" != "${MAJOR}" ]; then
              MINOR=0
              PATCH=0
            else
              # Try to infer bump type from PR source branch; fallback to commit subject
              SRC_BRANCH=""
              for i in 1 2 4; do
                RESP="$(curl -sS -H "Authorization: token ${GITHUB_PAT}" -H "Accept: application/vnd.github+json" \
                  "https://${GITHUB_HOST}/api/v3/repos/${GITHUB_OWNER}/${GITHUB_REPO}/commits/${HEAD_SHA}/pulls")" || true
                SRC_BRANCH="$(printf %s "${RESP}" | sed -n 's/.*"head":{[^}]*"ref":"\\([^"]*\\)".*/\\1/p' | head -n1)"
                [ -n "${SRC_BRANCH}" ] && break || sleep "${i}"
              done

              MERGE_SUBJ="$(git log -1 --pretty=%s)"
              if printf %s "${SRC_BRANCH:-}" | grep -Eiq '^(feat|feature)(/|-)'; then
                MINOR=$((LT_MINOR + 1)); PATCH=0
              elif printf %s "${MERGE_SUBJ}" | grep -Eiq '^Merge pull request #[0-9]+' && \
                   printf %s "${MERGE_SUBJ}" | grep -Eiq '(feat|feature)'; then
                MINOR=$((LT_MINOR + 1)); PATCH=0
              elif printf %s "${MERGE_SUBJ}" | grep -Eiq '^feat([(:]|[/-])'; then
                MINOR=$((LT_MINOR + 1)); PATCH=0
              else
                MINOR="${LT_MINOR}"; PATCH=$((LT_PATCH + 1))
              fi
            fi

            NEXT_VERSION="${TAG_PREFIX}${MAJOR}.${MINOR}.${PATCH}"
            echo "${NEXT_VERSION}" | tee next-version.txt

            # Create and push tag idempotently
            if ! git rev-parse -q --verify "refs/tags/${NEXT_VERSION}" >/dev/null; then
              git tag -a "${NEXT_VERSION}" -m "Release ${NEXT_VERSION} (automated by Jenkins)"
              git push origin "${NEXT_VERSION}"
            fi

            # Create CHANGELOG
            if [ -n "${RANGE_OPT}" ]; then
              {
                echo "Changes since ${LAST_TAG}:"
                echo
                git log --no-merges --pretty='* %h %s (%an)' "${RANGE_OPT}" || true
              } > CHANGELOG.txt
            else
              echo "Initial release." > CHANGELOG.txt
            fi

            # Drive GitHub CLI via env (no interactive login)
            export GH_TOKEN="${GITHUB_PAT}"
            export GH_HOST="${GITHUB_HOST}"

            # Create release idempotently
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

            # Upload fat/uber jar if present (keep default source archives)
            JAR_GLOB='app/build/libs/*-all.jar'
            if ls ${JAR_GLOB} >/dev/null 2>&1; then
              echo "Uploading fat jar(s):"
              ls -lh ${JAR_GLOB} || true
              gh release upload "${NEXT_VERSION}" ${JAR_GLOB} -R "${GITHUB_OWNER}/${GITHUB_REPO}" --clobber
            else
              echo "No fat jar found at ${JAR_GLOB}; skip upload."
            fi
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
