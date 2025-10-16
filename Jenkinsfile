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
          // Non-blocking mode: continue pipeline even if format check fails
          def result = sh(script: './gradlew verifyGoogleJavaFormat --no-daemon', returnStatus: true)
          if (result != 0) {
            echo '⚠️ Code format check failed. Please run ./gradlew googleJavaFormat locally.'
          } else {
            echo '✅ Code format check passed.'
          }

          // To make this stage blocking (fail the pipeline on format errors), replace the above block with:
          // sh './gradlew verifyGoogleJavaFormat --no-daemon'
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

    stage('Test') {
      steps {
        echo 'Running unit tests...'
        sh './gradlew test --no-daemon'
      }
      post {
        always {
          junit allowEmptyResults: true, testResults: '**/build/test-results/test/*.xml'
        }
      }
    }

    stage('Archive') {
      steps {
        archiveArtifacts artifacts: '**/build/libs/*.jar', fingerprint: true, onlyIfSuccessful: true
      }
    }

    stage('Deploy') {
      when {
        branch 'main'
      }
      steps {
        echo 'Deploying application...'
        sh 'echo "Deploy step placeholder. Add real deployment here if needed."'
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
