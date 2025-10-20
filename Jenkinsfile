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
          // Non-blocking: keep pipeline green even if format fails
          def result = sh(script: './gradlew verifyGoogleJavaFormat --no-daemon', returnStatus: true)
          if (result != 0) {
            echo '=( Code format check failed. Please run ./gradlew googleJavaFormat locally.'
          } else {
            echo '=) Code format check passed.'
          }
          // To make it blocking: sh './gradlew verifyGoogleJavaFormat --no-daemon'
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
          // (1) JUnit test results
          junit allowEmptyResults: true, testResults: '**/build/test-results/test/*.xml'

          // (2) Coverage (Coverage plugin parses JaCoCo XML)
          recordCoverage(
            tools: [[parser: 'JACOCO', pattern: '**/build/reports/jacoco/test/jacocoTestReport.xml']],
            sourceCodeRetention: 'LAST_BUILD',
            // Optional quality gates (mark build "unstable" if thresholds not met)
            qualityGates: [
              [metric: 'LINE',   threshold: 60.0, baseline: 'PROJECT', unstable: true],
              [metric: 'BRANCH', threshold: 60.0, baseline: 'PROJECT', unstable: true]
            ]
          )

          // (3) Publish JaCoCo HTML report for detailed browsing
          publishHTML(target: [
            reportDir: 'build/reports/jacoco/test/html',
            reportFiles: 'index.html',
            reportName: 'JaCoCo HTML',
            keepAll: true,
            allowMissing: true
          ])

          // (4) Optionally archive coverage artifacts
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
