pipeline {
  agent any
  options {
    timestamps()
    buildDiscarder(logRotator(numToKeepStr: '20'))
    skipDefaultCheckout(true)
  }

  environment {
    GRADLE_USER_HOME = "${WORKSPACE}/.gradle-cache"
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

    stage('Calculate Next Version (dry-run)') {
      when { not { branch 'main' } }
      environment {
        GITHUB_SERVER = 'https://github.sydney.edu.au/api/v3'
        REPO_SLUG     = 'SOFT2412-COMP9412-2025s2/A3-T28-G03'
      }
      steps {
        withCredentials([
          usernamePassword(credentialsId: 'ghe_https_pat', usernameVariable: 'GIT_USER', passwordVariable: 'GIT_TOKEN'),
          string(credentialsId: 'ghe_pat_secret', variable: 'GITHUB_PAT')
        ]) {
          sh '''
            set -euo pipefail

            # ensure authenticated remote for tag operations
            git remote set-url origin "https://$GIT_USER:$GIT_TOKEN@github.sydney.edu.au/$REPO_SLUG.git"
            git fetch --tags --prune
            [ -e .git/shallow ] && git fetch --unshallow || true

            # read major from file (developer-controlled)
            if [ ! -f VERSION_MAJOR ]; then echo "1" > VERSION_MAJOR; fi
            MAJOR=$(tr -d "\\n\\r" < VERSION_MAJOR)
            case "$MAJOR" in ''|*[!0-9]*) echo "Invalid VERSION_MAJOR: $MAJOR" >&2; exit 2 ;; esac

            # normalize last tag: strip leading v/V and any non-digit prefix
            LAST_TAG=$(git describe --tags --abbrev=0 2>/dev/null || true)
            if [ -z "$LAST_TAG" ]; then
              MINOR=0; PATCH=0
            else
              VT=$(printf "%s" "$LAST_TAG" | sed -E 's/^[^0-9]*([0-9].*)/\\1/')
              LT_MAJOR=$(printf "%s" "$VT" | cut -d. -f1)
              LT_MINOR=$(printf "%s" "$VT" | cut -d. -f2)
              LT_PATCH=$(printf "%s" "$VT" | cut -d. -f3)
              if [ "$LT_MAJOR" != "$MAJOR" ]; then
                MINOR=0; PATCH=0
              else
                MINOR=$LT_MINOR; PATCH=$LT_PATCH
              fi
            fi

            HEAD_SHA=$(git rev-parse HEAD)
            SRC_BRANCH=""

            # API-first (POSIX-safe retry: 1,2,4 seconds)
            for DELAY in 1 2 4; do
              RESP=$(curl -sS -H "Authorization: token $GITHUB_PAT" -H "Accept: application/vnd.github+json" \
                "$GITHUB_SERVER/repos/$REPO_SLUG/commits/$HEAD_SHA/pulls" || true)
              SRC_BRANCH=$(printf "%s" "$RESP" | sed -n 's/.*"head":{[^}]*"ref":"\\([^"]*\\)".*/\\1/p' | head -n1)
              [ -n "$SRC_BRANCH" ] && break
              sleep "$DELAY"
            done

            # title fallback for merge commits
            if [ -z "$SRC_BRANCH" ]; then
              MERGE_SUBJ=$(git log -1 --pretty=%s || true)
              if printf "%s" "$MERGE_SUBJ" | grep -Eiq '^Merge pull request #[0-9]+'; then
                SRC_BRANCH=$(printf "%s" "$MERGE_SUBJ" | sed -n 's#.* from [^/]*/\\([^ )]*\\).*#\\1#p')
              fi
            fi

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

            # keep same tag style as repository (detected from LAST_TAG)
            if printf "%s" "$LAST_TAG" | grep -q '^[V]'; then
              NEXT_VERSION="V${MAJOR}.${MINOR}.${PATCH}"
            else
              NEXT_VERSION="v${MAJOR}.${MINOR}.${PATCH}"
            fi

            echo "$NEXT_VERSION" | tee next-version.txt
          '''
          archiveArtifacts artifacts: 'next-version.txt', fingerprint: true, allowEmptyArchive: false
          echo 'Dry-run only: calculated next version (see next-version.txt)'
        }
      }
    }
  }

  post {
    success { echo 'Pipeline completed successfully.' }
    failure { echo 'Pipeline failed.' }
  }
}
