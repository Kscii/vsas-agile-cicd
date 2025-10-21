pipeline {
  agent any

  options {
    timestamps()
  }

  environment {
    GRADLE_USER_HOME = "${WORKSPACE}/.gradle-cache"
    GITHUB_HOST  = 'github.sydney.edu.au'
    GITHUB_OWNER = 'SOFT2412-COMP9412-2025s2'
    GITHUB_REPO  = 'A3-T28-G03'
    // No test-mode on main
  }

  stages {
    stage('Checkout') {
      steps {
        echo "Checking out branch: ${env.BRANCH_NAME}"
        deleteDir()
        checkout([
          $class: 'GitSCM',
          branches: [[name: "*/${env.BRANCH_NAME}"]],
          doGenerateSubmoduleConfigurations: false,
          extensions: [[$class: 'CloneOption', noTags: true, shallow: false, depth: 0]],
          userRemoteConfigs: [[
            url: "https://github.sydney.edu.au/${env.GITHUB_OWNER}/${env.GITHUB_REPO}.git",
            credentialsId: 'ghe_https_pat'
          ]]
        ])
      }
    }

    stage('Sanity') {
      steps {
        sh '''
          echo "== PWD =="
          pwd
          echo "== Root listing =="
          ls -la
          echo "== Wrapper listing =="
          ls -la gradlew gradle/wrapper || true
          echo "== GRADLE_USER_HOME =="
          echo "${GRADLE_USER_HOME}"
        '''
      }
    }

    stage('Prepare Gradle cache') {
      steps {
        sh '''
          set -e
          mkdir -p "${GRADLE_USER_HOME}"
          chmod -R u+rwX "${GRADLE_USER_HOME}"
        '''
      }
    }

    stage('Format (verify)') {
      steps {
        echo 'Running google-java-format verify...'
        script {
          sh './gradlew --no-daemon -g "${GRADLE_USER_HOME}" verifyGoogleJavaFormat'
        }
        echo '=) Code format check passed.'
      }
    }

    stage('Build') {
      steps {
        echo 'Building Gradle project...'
        sh '''
          chmod +x gradlew
          ./gradlew --no-daemon -g "${GRADLE_USER_HOME}" clean assemble
        '''
      }
    }

    stage('Test & Coverage') {
      steps {
        echo 'Running unit tests and generating JaCoCo XML/HTML reports...'
        sh './gradlew --no-daemon -g "${GRADLE_USER_HOME}" test jacocoTestReport'
      }
      post {
        always {
          junit allowEmptyResults: true, testResults: 'app/build/test-results/test/*.xml'
          recordCoverage(tools: [jacoco(pattern: '**/build/reports/jacoco/test/jacocoTestReport.xml')])
          publishHTML(target: [
            reportDir: 'app/build/reports/jacoco/test/html',
            reportFiles: 'index.html',
            reportName: 'JaCoCo HTML'
          ])
          archiveArtifacts artifacts: 'app/build/distributions/*, app/build/libs/*, next-version.txt, CHANGELOG.txt', onlyIfSuccessful: false
        }
      }
    }

    stage('Archive') {
      steps {
        archiveArtifacts artifacts: 'app/build/**', fingerprint: true
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
      when { branch 'main' }
      environment {
        // release uses a bot/service PAT in Jenkins credentials
        GH_HOST = "${GITHUB_HOST}"
      }
      steps {
        withCredentials([string(credentialsId: 'ghe_https_pat', variable: 'GITHUB_PAT')]) {
          withEnv(["GH_TOKEN=${GITHUB_PAT}"]) {
            sh '''
              set -euo pipefail

              # Ensure remote uses PAT for pushing tags
              git remote set-url origin "https://jenkins:${GITHUB_PAT}@${GITHUB_HOST}/${GITHUB_OWNER}/${GITHUB_REPO}.git"
              git fetch --tags --prune

              git config user.name  "xfan0282"
              git config user.email "xfan0282@uni.sydney.edu.au"

              # Read major series (default to 1 if missing)
              if [ -f VERSION_MAJOR ]; then
                MAJOR="$(tr -d '\\n\\r' < VERSION_MAJOR)"
              else
                MAJOR="1"
              fi

              # Last tag (may be absent on brand-new repo)
              LAST_TAG="$(git describe --tags --abbrev=0 || true)"
              if [ -n "${LAST_TAG}" ]; then
                VT="$(printf "%s" "${LAST_TAG}" | sed -E 's/^[^0-9]*([0-9].*)/\\1/')"
                LT_MAJOR="$(printf "%s" "${VT}" | cut -d. -f1)"
                LT_MINOR="$(printf "%s" "${VT}" | cut -d. -f2)"
                LT_PATCH="$(printf "%s" "${VT}" | cut -d. -f3)"
              else
                LT_MAJOR="0"; LT_MINOR="0"; LT_PATCH="0"
              fi

              # Determine tag prefix (V/v)
              if printf "%s" "${LAST_TAG:-}" | grep -q '^[V]'; then
                TAG_PREFIX="V"
              else
                TAG_PREFIX="v"
              fi

              # Compute next version (simple rule: new major if VERSION_MAJOR bumped; else patch++)
              if [ "${LT_MAJOR}" != "${MAJOR}" ]; then
                MINOR="0"; PATCH="0"
              else
                MINOR="${LT_MINOR}"
                PATCH=$(( ${LT_PATCH} + 1 ))
              fi

              NEXT_VERSION="${TAG_PREFIX}${MAJOR}.${MINOR}.${PATCH}"
              echo "${NEXT_VERSION}" | tee next-version.txt

              # Tag if not exists
              if ! git rev-parse -q --verify "refs/tags/${NEXT_VERSION}" >/dev/null; then
                git tag -a "${NEXT_VERSION}" -m "Release ${NEXT_VERSION} (automated by Jenkins)"
                git push origin "${NEXT_VERSION}"
              fi

              # Build changelog since last tag
              if [ -n "${LAST_TAG}" ]; then
                RANGE_OPT="${LAST_TAG}..HEAD"
                {
                  echo "Changes since ${LAST_TAG}:"
                  echo
                  git log --no-merges --pretty='* %h %s (%an)' "${RANGE_OPT}"
                } > CHANGELOG.txt
              else
                {
                  echo "Initial release ${NEXT_VERSION}"
                  echo
                  git log --no-merges --pretty='* %h %s (%an)'
                } > CHANGELOG.txt
              fi

              # Resolve commit SHA for --target and guard -u
              HEAD_SHA="$(git rev-parse HEAD)"

              # Install gh if missing (idempotent)
              if ! command -v gh >/dev/null 2>&1; then
                echo "gh not found on agent; please pre-install or bake into image."
                exit 1
              fi

              # Idempotent release creation
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
            '''
            archiveArtifacts artifacts: 'next-version.txt, CHANGELOG.txt', fingerprint: true
          }
        }
      }
    }
  }

  post {
    success {
      echo 'Pipeline completed successfully.'
    }
    failure {
      echo 'Pipeline failed.'
    }
  }
}
