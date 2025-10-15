pipeline {
    agent any

    stages {
        stage('Checkout') {
            steps {
                echo "Checking out branch: ${env.BRANCH_NAME}"
                checkout scm
            }
        }

        stage('Build') {
            steps {
                echo 'Building Gradle project...'
                sh './gradlew clean build'
            }
        }

        stage('Test') {
            steps {
                echo 'Running unit tests...'
                sh './gradlew test'
            }
            post {
                always {
                    junit 'build/test-results/test/*.xml'
                }
            }
        }

        stage('Deploy') {
            when {
                branch 'main'
            }
            steps {
                echo 'Deploying application...'
                sh '''
                    mkdir -p /Users/kscii/Desktop/deploy-output
                    cp build/libs/*.jar /Users/kscii/Desktop/deploy-output/
                '''
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
