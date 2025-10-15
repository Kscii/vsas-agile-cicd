pipeline {
  agent any
  options {
    timestamps()
    ansiColor('xterm')
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

    stage('Build') {
      steps {
        echo 'Building Gradle project...'
        sh '''
          chmod +x gradlew || true
          ./gradlew --version
          ./gradlew clean assemble -x verifyGoogleJavaFormat --no-daemon
        '''
      }
    }

    stage('Test') {
      steps {
        echo 'Running unit tests...'
        sh '''
          ./gradlew test -x verifyGoogleJavaFormat --no-daemon
        '''
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
