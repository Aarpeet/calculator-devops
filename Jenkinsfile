// Declarative CI/CD pipeline for the Calculator REST API.
//
//   Checkout -> Build -> Test -> Docker Build -> Push -> Deploy  (+ email on success/failure)
//
// Re-runnable and idempotent: every run deploys an immutable image tag (the build
// number) into the same fixed Compose project, so a re-run only replaces the
// calculator-app container. Concurrent builds are disabled so two deploys never
// race against the same stack.

pipeline {
    agent any

    triggers {
        githubPush()                  // GitHub webhook (preferred)
        pollSCM('H/5 * * * *')        // fallback when the webhook cannot reach Jenkins
    }

    options {
        disableConcurrentBuilds()
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    environment {
        DOCKER_IMAGE          = "aarpeet/calculator-api"
        IMAGE_TAG             = "${env.BUILD_NUMBER}"
        // Jenkins-managed "Username with password" credential; exposes
        // DOCKERHUB_CREDENTIALS_USR and DOCKERHUB_CREDENTIALS_PSW (masked in logs)
        DOCKERHUB_CREDENTIALS = credentials('dockerhub-creds')
        APP_HOST_PORT         = "8082"    // calculator-app host port (Jenkins itself owns 8080)
        PROXY_HOST_PORT       = "8081"    // nginx-proxy host port
        NOTIFY_EMAIL          = "Aarpeet135@gmail.com"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                sh 'mvn clean package -DskipTests -B'
            }
        }

        stage('Test') {
            steps {
                // A failing test makes Maven exit non-zero, which fails this stage and the build
                sh 'mvn test -B'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                    archiveArtifacts artifacts: 'target/site/jacoco/**', allowEmptyArchive: true
                }
            }
        }

        stage('Docker Build') {
            steps {
                // --pull re-checks the base images so rebuilds pick up upstream security patches
                sh '''
                    docker build --pull \
                        --build-arg BUILD_NUMBER=${IMAGE_TAG} \
                        --build-arg VCS_REF=${GIT_COMMIT:-unknown} \
                        --build-arg BUILD_DATE=$(date -u +%Y-%m-%dT%H:%M:%SZ) \
                        -t ${DOCKER_IMAGE}:${IMAGE_TAG} -t ${DOCKER_IMAGE}:latest .
                '''
            }
        }

        stage('Push') {
            steps {
                // Single-quoted so the shell (not Groovy) expands the secret; it never
                // appears in the command line or the build log.
                sh '''
                    echo $DOCKERHUB_CREDENTIALS_PSW | docker login -u $DOCKERHUB_CREDENTIALS_USR --password-stdin
                    docker push ${DOCKER_IMAGE}:${IMAGE_TAG}
                    docker push ${DOCKER_IMAGE}:latest
                '''
            }
            post {
                always {
                    sh 'docker logout'
                }
            }
        }

        stage('Deploy') {
            steps {
                sh '''
                    DOCKER_IMAGE=${DOCKER_IMAGE}:${IMAGE_TAG} docker compose up -d --remove-orphans

                    echo "Verifying deployment of ${DOCKER_IMAGE}:${IMAGE_TAG} ..."
                    for attempt in $(seq 1 30); do
                        health=$(curl -fsS "http://localhost:${APP_HOST_PORT}/actuator/health" || true)
                        sum=$(curl -fsS "http://localhost:${PROXY_HOST_PORT}/api/v1/add?a=2&b=3" || true)
                        # The build number reported through the proxy proves the NEW container
                        # is serving traffic, not a previous one that is still healthy
                        info=$(curl -fsS "http://localhost:${PROXY_HOST_PORT}/actuator/info" || true)
                        if echo "$health" | grep -q '"status":"UP"' && echo "$sum" | grep -q '"result":5}' && echo "$info" | grep -q '"build":"'"${IMAGE_TAG}"'"'; then
                            echo "Deployment healthy after ${attempt} attempt(s)"
                            echo "  app   (:${APP_HOST_PORT}/actuator/health): $health"
                            echo "  proxy (:${PROXY_HOST_PORT}/api/v1/add?a=2&b=3): $sum"
                            echo "  info  (:${PROXY_HOST_PORT}/actuator/info): $info"
                            docker compose ps
                            exit 0
                        fi
                        echo "Attempt ${attempt}/30: not healthy yet, retrying in 5s"
                        sleep 5
                    done

                    echo "Deployment did not become healthy in time"
                    docker compose ps
                    docker compose logs --tail=100
                    exit 1
                '''
            }
        }
    }

    post {
        success {
            script { notifyByEmail('SUCCESS') }
        }
        failure {
            script { notifyByEmail('FAILURE') }
        }
        cleanup {
            // Keep only the 3 newest build-number tags locally; tags still used by a
            // running container are skipped (docker rmi refuses without -f).
            sh '''
                docker images ${DOCKER_IMAGE} --format '{{.Tag}}' | grep -E '^[0-9]+$' | sort -rn | tail -n +4 | while read -r tag; do
                    docker rmi ${DOCKER_IMAGE}:${tag} >/dev/null 2>&1 || true
                done
            '''
        }
    }
}

// Email notification via the Email Extension plugin, falling back to the core
// Mailer step. Mail problems are logged but never change the build result.
def notifyByEmail(String status) {
    def subject = "[Jenkins] ${status}: ${env.JOB_NAME} #${env.BUILD_NUMBER}"
    def body = """Build ${status}

Job      : ${env.JOB_NAME}
Build    : #${env.BUILD_NUMBER}
Image    : ${env.DOCKER_IMAGE}:${env.IMAGE_TAG}
Duration : ${currentBuild.durationString.replace(' and counting', '')}
Build URL: ${env.BUILD_URL}
Console  : ${env.BUILD_URL}console
"""
    try {
        emailext(to: env.NOTIFY_EMAIL, subject: subject, body: body,
                 mimeType: 'text/plain', attachLog: status == 'FAILURE')
    } catch (Throwable emailextError) {
        echo "emailext failed (${emailextError}); falling back to mail"
        try {
            mail(to: env.NOTIFY_EMAIL, subject: subject, body: body)
        } catch (Throwable mailError) {
            echo "mail failed as well (${mailError}); check Manage Jenkins > System > E-mail settings"
        }
    }
}
