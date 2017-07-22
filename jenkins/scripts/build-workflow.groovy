node {
    git poll: true, branch: 'development', credentialsId: 'fc417088-73fb-4642-a7f4-aa827980386c', url: 'ssh://APKAIEHC6B4UNIPCJBEA@git-codecommit.eu-west-1.amazonaws.com/v1/repos/pcp-core'

    def version = version(readFile('pom.xml'))

    echo "Building version ${version}"

    stage 'Build Artifacts'
    withEnv(['DART_HOME=/usr/lib/dart']) {
        def server = Artifactory.server('-1695128938@1445245612561')

        def rtMaven = Artifactory.newMavenBuild()
        rtMaven.deployer server: server, releaseRepo: 'middleware-releases', snapshotRepo: 'middleware-application'
        rtMaven.deployer.artifactDeploymentPatterns.addExclude("**/*-tests.jar")
        rtMaven.tool = 'maven3.3.3'

        def buildInfo = rtMaven.run pom: 'pom.xml', goals: 'clean install'

        server.publishBuildInfo buildInfo
    }

    stage 'Build Docker Images'
    withEnv(["POM_VERSION=${version}"]) {
        sh "jenkins/scripts/build-and-push-docker-images.sh"
    }

    stage "Deploy JSON Schemas"
    deploySchema(
            "$WORKSPACE/canonical-parent/canonical-model-core/src/main/resources/schema",
            "schema-canonical-normalized-${version}.zip",
            version
    )
    deploySchema(
            "$WORKSPACE/canonical-parent/canonical-model-denormalized/src/main/resources/schema",
            "schema-canonical-denormalized-${version}.zip",
            version
    )
    deploySchema(
            "$WORKSPACE/serializer-parent/serializer-aem-b2b-parent/serializer-aem-b2b-model/src/main/resources/schema",
            "schema-serializer-aem-b2b-${version}.zip",
            version
    )
    deploySchema(
            "$WORKSPACE/serializer-parent/serializer-aem-b2c-parent/serializer-aem-b2c-model/src/main/resources/schema",
            "schema-serializer-aem-b2c-${version}.zip",
            version
    )
    deploySchema(
            "$WORKSPACE/serializer-parent/serializer-advisor-parent/serializer-advisor-model/src/main/resources/schema",
            "schema-serializer-advisor-${version}.zip",
            version
    )
    deploySchema(
            "$WORKSPACE/serializer-parent/serializer-pcmsshop-parent/serializer-pcmsshop-model/src/main/resources/schema",
            "schema-serializer-pcmsshop-${version}.zip",
            version
    )
    deploySchema(
            "$WORKSPACE/serializer-parent/serializer-hhsshop-b2b-parent/serializer-hhsshop-b2b-model/src/main/resources/schema",
            "schema-serializer-hhsshop-b2b-${version}.zip",
            version
    )
    deploySchema(
            "$WORKSPACE/serializer-parent/serializer-hhsshop-b2c-parent/serializer-hhsshop-b2c-model/src/main/resources/schema",
            "schema-serializer-hhsshop-b2c-${version}.zip",
            version
    )

    stage 'Deploy Swagger UI Documentation'
//    sh "rsync -avz --rsh=\"ssh\" api-swagger-ui/dist/ apidoc@10.64.96.42:/srv/api/doc/"
//    sh "aws s3 cp s3://philips-mw-artifacts/swagger/mw-test-swagger.yaml /tmp/swagger.yaml"
//    sh "scp /tmp/swagger.yaml apidoc@10.64.96.42:/srv/api/doc/swagger.yaml"
//    sh "rm -f /tmp/swagger.yaml"

    stage 'Deploy PCP Documentation'
//    sh "rsync -avz --rsh=\"ssh\" documentation/target/generated-docs/ apidoc@10.64.96.42:/srv/documentation/"

    stage "Plan Environment Update"
    sh "./terraform-console -q -b -p=philips-stibo --no-role --no-color -w=${WORKSPACE} pcp.bash plan develop"

    timeout(time: 2, unit: 'DAYS') {
        input "Continue with deployment?"
    }

    stage 'Update Environment'
    sh "./terraform-console -q -b -p=philips-stibo --no-role --no-color -w=${WORKSPACE} pcp.bash apply develop"
    //Run twice in order to make sure Lambda functions are updated
    sh "./terraform-console -q -b -p=philips-stibo --no-role --no-color -w=${WORKSPACE} pcp.bash apply develop"

    stopTasksForService("mw_develop_mgmtgui_service");
    stopTasksForService("mw_develop_mgmt_service")
    stopTasksForService("ser_aem-b2b")
    stopTasksForService("ser_aem-b2c")
    stopTasksForService("mw_develop_ser_advisor_service")
    stopTasksForService("ser_pcmsshop")
    stopTasksForService("ser_hhsshop-b2b")
    stopTasksForService("ser_hhsshop-b2c")
    stopTasksForService("mw_develop_canonical_apibackend_service")
    stopTasksForService("mw_develop_cq5_apibackend_service")
    stopTasksForService("mw_develop_healthmonitor_service")

    stage 'Integration Tests'
    build job: 'cmw-integration', parameters: [[$class: 'StringParameterValue', name: 'PREFIX', value: 'mw_develop'], [$class: 'StringParameterValue', name: 'BRANCH', value: 'development'], [$class: 'StringParameterValue', name: 'HOST', value: 'https://api-mw-develop.productcontentpublication.philips.com/'], [$class: 'StringParameterValue', name: 'API_KEY', value: 'nIx3Ya40dm6Id6dUjFInq41IeuvZ1ZBx2IMJu6uU']]
}

@NonCPS
def version(text) {
    def project = new XmlSlurper().parseText(text)
    project.version.text()
}

def deploySchema(String directory, String zipFilename, String version) {
    dir(directory) {
        sh "zip -r $zipFilename *"
        sh "aws s3 cp $zipFilename s3://philips-mw-artifacts/schemas/${version}/$zipFilename"
        sh "rm $zipFilename"
    }
}

def stopTasksForService(String serviceName) {
    sh """
aws ecs list-tasks --cluster "mw_develop" --service-name $serviceName | grep arn | sed -n -e 's/^.*\\(arn.*\\)"\$/\\1/p' | while read -r task ; do
    aws ecs stop-task --cluster "mw_develop" --task "\$task"
done
"""
}