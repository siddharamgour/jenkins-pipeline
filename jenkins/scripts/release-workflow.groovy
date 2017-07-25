node {
    if (NEXT_RELEASE_VERSION == null || NEXT_RELEASE_VERSION.trim().equals("")) {
        error 'No value was provided for NEXT_RELEASE_VERSION'
    }

    if (BRANCH == null || BRANCH.trim().equals("")) {
        error 'No value was provided for BRANCH'
    }

    String maven = "${tool 'maven3.3.3'}/bin"

    //Clean workspace
    sh 'rm -rf *'
    sh 'rm -rf .git'
    sh 'rm -rf .gitignore'

    git branch: "${BRANCH}", credentialsId: 'fc417088-73fb-4642-a7f4-aa827980386c', poll: false, url: 'ssh://APKAIEHC6B4UNIPCJBEA@git-codecommit.eu-west-1.amazonaws.com/v1/repos/pcp-core'

    //Merge ${BRANCH} to master
    sh 'git checkout -b master origin/master'
    sh "git merge origin/${BRANCH}"

    //Determine version to release
    def version = version(readFile('pom.xml'))

    echo "=================================================\n=     Starting release of version ${version}    =\n================================================="

    //Remove snapshot from version
    sh "${maven}/mvn versions:set -DnewVersion=${version} -DgenerateBackupPoms=false"

    //Commit files to repository
    sh 'find . -name pom.xml -exec git add {} \\;'
    sh "git commit -m \"Releases version ${version} of the project\""

    //Tag release revision
    sh "git tag -a \"RELEASE-${version}\" -m \'Release ${version}, tagged by Jenkins\'"

    //Bump version on ${BRANCH} branch
    sh "git checkout ${BRANCH}"

    //Merge in the changes on master
    sh 'git merge master'

    //Update version
    sh "${maven}/mvn versions:set -DnewVersion=${NEXT_RELEASE_VERSION} -DgenerateBackupPoms=false"

    //Commit files to repository
    sh 'find . -name pom.xml -exec git add {} \\;'
    sh "git commit -m \"Increments version to ${NEXT_RELEASE_VERSION}\""

    //Checkout master branch again
    sh 'git checkout master'

    //Run build
    withEnv(['DART_HOME=/usr/lib/dart']) {
        sh "${maven}/mvn clean install"
    }

    //Build base Docker images
    dir('devops/docker') {
        sh "docker build -t 012021845862.dkr.ecr.us-east-1.amazonaws.com/java:${version} -t 012021845862.dkr.ecr.eu-west-1.amazonaws.com/java:${version} Java/"
        sh "docker build -t 012021845862.dkr.ecr.us-east-1.amazonaws.com/logging:${version} -t 012021845862.dkr.ecr.eu-west-1.amazonaws.com/logging:${version} Logging/"
        sh "docker build -t 012021845862.dkr.ecr.us-east-1.amazonaws.com/logstash:${version} -t 012021845862.dkr.ecr.eu-west-1.amazonaws.com/logstash:${version} Logstash/"
        sh "docker build -t 012021845862.dkr.ecr.us-east-1.amazonaws.com/tomcat8:${version}  -t 012021845862.dkr.ecr.eu-west-1.amazonaws.com/tomcat8:${version} Tomcat/"

        //Push base images
        sh 'eval $(aws ecr get-login --region eu-west-1)'
        sh "docker push 012021845862.dkr.ecr.eu-west-1.amazonaws.com/java:${version}"
        sh "docker push 012021845862.dkr.ecr.eu-west-1.amazonaws.com/logging:${version}"
        sh "docker push 012021845862.dkr.ecr.eu-west-1.amazonaws.com/logstash:${version}"
        sh "docker push 012021845862.dkr.ecr.eu-west-1.amazonaws.com/tomcat8:${version}"
        sh "docker logout"

        sh 'eval $(aws ecr get-login --region us-east-1)'
        sh "docker push 012021845862.dkr.ecr.us-east-1.amazonaws.com/java:${version}"
        sh "docker push 012021845862.dkr.ecr.us-east-1.amazonaws.com/logging:${version}"
        sh "docker push 012021845862.dkr.ecr.us-east-1.amazonaws.com/logstash:${version}"
        sh "docker push 012021845862.dkr.ecr.us-east-1.amazonaws.com/tomcat8:${version}"
        sh "docker logout"
    }

    //Build PCP Docker images
    dir('canonical-parent/canonical-tree-generator/target') {
        sh "docker build -t 012021845862.dkr.ecr.us-east-1.amazonaws.com/tree-generator:${version} -t 012021845862.dkr.ecr.eu-west-1.amazonaws.com/tree-generator:${version} ."
    }

    dir('serializer-parent/serializer-base/serializer-base-queue-populater/target') {
        sh "docker build -t 012021845862.dkr.ecr.us-east-1.amazonaws.com/queuepopulator:${version} -t 012021845862.dkr.ecr.eu-west-1.amazonaws.com/queuepopulator:${version} ."
    }

    dir('serializer-parent/serializer-cq5-parent/serializer-cq5-executor/target') {
        sh "docker build -t 012021845862.dkr.ecr.us-east-1.amazonaws.com/cq5-executor:${version} -t 012021845862.dkr.ecr.eu-west-1.amazonaws.com/cq5-executor:${version} ."
    }

    dir('serializer-parent/serializer-pdf-parent/serializer-pdf-executor/target') {
        sh "docker build -t 012021845862.dkr.ecr.us-east-1.amazonaws.com/pdf-executor:${version} -t 012021845862.dkr.ecr.eu-west-1.amazonaws.com/pdf-executor:${version} ."
    }

    dir('serializer-parent/serializer-pdf-parent/serializer-pdf-transport-service/target') {
        sh "docker build -t 012021845862.dkr.ecr.us-east-1.amazonaws.com/pdf-transport-service:${version} -t 012021845862.dkr.ecr.eu-west-1.amazonaws.com/pdf-transport-service:${version} ."
    }

    dir('serializer-parent/serializer-bazaarvoice-parent/serializer-bazaarvoice-executor/target') {
        sh "docker build -t 012021845862.dkr.ecr.us-east-1.amazonaws.com/bazaarvoice-executor:${version} -t 012021845862.dkr.ecr.eu-west-1.amazonaws.com/bazaarvoice-executor:${version} ."
    }

    dir('serializer-parent/serializer-pikachu-parent/serializer-pikachu-executor/target') {
        sh "docker build -t 012021845862.dkr.ecr.us-east-1.amazonaws.com/pikachu-executor:${version} -t 012021845862.dkr.ecr.eu-west-1.amazonaws.com/pikachu-executor:${version} ."
    }

    dir('tsunami-executor/target') {
        sh "docker build -t 012021845862.dkr.ecr.us-east-1.amazonaws.com/tsunami-executor:${version} -t 012021845862.dkr.ecr.eu-west-1.amazonaws.com/tsunami-executor:${version} ."
    }

    dir('elasticsearch-log-cleaner/target') {
        sh "docker build -t 012021845862.dkr.ecr.us-east-1.amazonaws.com/elasticsearch-log-cleaner:${version} -t 012021845862.dkr.ecr.eu-west-1.amazonaws.com/elasticsearch-log-cleaner:${version} ."
    }

    dir('smoke-writer/target') {
        sh "docker build -t 012021845862.dkr.ecr.us-east-1.amazonaws.com/smoke-writer:${version} -t 012021845862.dkr.ecr.eu-west-1.amazonaws.com/smoke-writer:${version} ."
    }

    dir('object-transport-server/target') {
        sh "docker build -t 012021845862.dkr.ecr.us-east-1.amazonaws.com/ots:${version} -t 012021845862.dkr.ecr.eu-west-1.amazonaws.com/ots:${version} ."
    }

    dir('gsn/target') {
        sh "docker build -t 012021845862.dkr.ecr.us-east-1.amazonaws.com/gsn:${version} -t 012021845862.dkr.ecr.eu-west-1.amazonaws.com/gsn:${version} ."
    }

    dir('management-server/target') {
        sh "docker build -t 012021845862.dkr.ecr.us-east-1.amazonaws.com/mgmt:${version} -t 012021845862.dkr.ecr.eu-west-1.amazonaws.com/mgmt:${version} ."
    }

    dir('management-gui/target') {
        sh "docker build -t 012021845862.dkr.ecr.us-east-1.amazonaws.com/mgmtgui:${version} -t 012021845862.dkr.ecr.eu-west-1.amazonaws.com/mgmtgui:${version} ."
    }

    //Build Canonical API docker image
    dir('api-parent/api-canonical-parent/api-canonical-rest/target') {
        sh "docker build -t 012021845862.dkr.ecr.us-east-1.amazonaws.com/canonical-api:${version} -t 012021845862.dkr.ecr.eu-west-1.amazonaws.com/canonical-api:${version} ."
    }

    //Build CQ5 API docker image
    dir('api-parent/api-cq5-parent/api-cq5-rest/target') {
        sh "docker build -t 012021845862.dkr.ecr.us-east-1.amazonaws.com/cq5-api:${version} -t 012021845862.dkr.ecr.eu-west-1.amazonaws.com/cq5-api:${version} ."
    }

    //Build API Consumer docker image
    dir('api-consumer/target') {
        sh "docker build -t 012021845862.dkr.ecr.us-east-1.amazonaws.com/api-consumer:${version} -t 012021845862.dkr.ecr.eu-west-1.amazonaws.com/api-consumer:${version} ."
    }

    //Push Docker images to eu-west-1
    sh 'eval $(aws ecr get-login --region eu-west-1)'
    sh "docker push 012021845862.dkr.ecr.eu-west-1.amazonaws.com/tree-generator:${version}"
    sh "docker push 012021845862.dkr.ecr.eu-west-1.amazonaws.com/queuepopulator:${version}"
    sh "docker push 012021845862.dkr.ecr.eu-west-1.amazonaws.com/cq5-executor:${version}"
    sh "docker push 012021845862.dkr.ecr.eu-west-1.amazonaws.com/pdf-executor:${version}"
    sh "docker push 012021845862.dkr.ecr.eu-west-1.amazonaws.com/pdf-transport-service:${version}"
    sh "docker push 012021845862.dkr.ecr.eu-west-1.amazonaws.com/bazaarvoice-executor:${version}"
    sh "docker push 012021845862.dkr.ecr.eu-west-1.amazonaws.com/pikachu-executor:${version}"
    sh "docker push 012021845862.dkr.ecr.eu-west-1.amazonaws.com/tsunami-executor:${version}"
    sh "docker push 012021845862.dkr.ecr.eu-west-1.amazonaws.com/elasticsearch-log-cleaner:${version}"
    sh "docker push 012021845862.dkr.ecr.eu-west-1.amazonaws.com/smoke-writer:${version}"
    sh "docker push 012021845862.dkr.ecr.eu-west-1.amazonaws.com/ots:${version}"
    sh "docker push 012021845862.dkr.ecr.eu-west-1.amazonaws.com/gsn:${version}"
    sh "docker push 012021845862.dkr.ecr.eu-west-1.amazonaws.com/mgmt:${version}"
    sh "docker push 012021845862.dkr.ecr.eu-west-1.amazonaws.com/mgmtgui:${version}"
    sh "docker push 012021845862.dkr.ecr.eu-west-1.amazonaws.com/canonical-api:${version}"
    sh "docker push 012021845862.dkr.ecr.eu-west-1.amazonaws.com/cq5-api:${version}"
    sh "docker push 012021845862.dkr.ecr.eu-west-1.amazonaws.com/api-consumer:${version}"
    sh "docker logout"

    //Push Docker images to us-east-1
    sh 'eval $(aws ecr get-login --region us-east-1)'
    sh "docker push 012021845862.dkr.ecr.us-east-1.amazonaws.com/tree-generator:${version}"
    sh "docker push 012021845862.dkr.ecr.us-east-1.amazonaws.com/queuepopulator:${version}"
    sh "docker push 012021845862.dkr.ecr.us-east-1.amazonaws.com/cq5-executor:${version}"
    sh "docker push 012021845862.dkr.ecr.us-east-1.amazonaws.com/pdf-executor:${version}"
    sh "docker push 012021845862.dkr.ecr.us-east-1.amazonaws.com/pdf-transport-service:${version}"
    sh "docker push 012021845862.dkr.ecr.us-east-1.amazonaws.com/bazaarvoice-executor:${version}"
    sh "docker push 012021845862.dkr.ecr.us-east-1.amazonaws.com/pikachu-executor:${version}"
    sh "docker push 012021845862.dkr.ecr.us-east-1.amazonaws.com/tsunami-executor:${version}"
    sh "docker push 012021845862.dkr.ecr.us-east-1.amazonaws.com/elasticsearch-log-cleaner:${version}"
    sh "docker push 012021845862.dkr.ecr.us-east-1.amazonaws.com/smoke-writer:${version}"
    sh "docker push 012021845862.dkr.ecr.us-east-1.amazonaws.com/ots:${version}"
    sh "docker push 012021845862.dkr.ecr.us-east-1.amazonaws.com/gsn:${version}"
    sh "docker push 012021845862.dkr.ecr.us-east-1.amazonaws.com/mgmt:${version}"
    sh "docker push 012021845862.dkr.ecr.us-east-1.amazonaws.com/mgmtgui:${version}"
    sh "docker push 012021845862.dkr.ecr.us-east-1.amazonaws.com/canonical-api:${version}"
    sh "docker push 012021845862.dkr.ecr.us-east-1.amazonaws.com/cq5-api:${version}"
    sh "docker push 012021845862.dkr.ecr.us-east-1.amazonaws.com/api-consumer:${version}"
    sh "docker logout"

    //Push changes to remote repository
    sh "git checkout ${BRANCH}"

    //Retry pushing to ${BRANCH} several times because it could be that a developer pushes before this finishes
    //Retry disabled because it fails with the last version of the pipeline plugin on Jenkins
    //retry(5) {
    //sh "git branch --set-upstream-to ${BRANCH}"
    //sh 'git pull --no-edit'
    sh "git push origin ${BRANCH}"
    //}

    sh 'git push origin master'
    sh "git push origin \"RELEASE-${version}\""

    //Publish artifacts to Artifactory
    build job: 'cmw-publish-artifacts-3.0.0'

    //Build Terraform archive
    build job: 'cmw-build-devops-3.0.0', parameters: [[$class: 'StringParameterValue', name: 'BRANCH', value: "refs/tags/RELEASE-${version}"]]
}

@NonCPS
def version(text) {
    def project = new XmlSlurper().parseText(text)
    project.version.text().replace("-SNAPSHOT", "")
}
