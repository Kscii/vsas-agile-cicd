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
  }

  post {
    success { echo 'Pipeline completed successfully.' }
    failure { echo 'Pipeline failed.' }
  }
}
