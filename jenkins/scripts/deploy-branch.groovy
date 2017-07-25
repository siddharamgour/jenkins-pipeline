node {
   		//$BRANCH
		//$ENVIRONMENT
		//$CLASSIFIER
	if (BRANCH == null || BRANCH.trim().equals("")) {
        error 'No value was provided for BRANCH'
    }
		
	String mavenHome = "${tool 'M3_0_5'}/bin"
	
	stage "Build Artifacts for $BRANCH"
		timeout(time: 2, unit: 'MINUTES') {
            input "Continue with deployment?"
        }
        sh "${mavenHome}/mvn versions:set -DnewVersion=${CLASSIFIER} -DgenerateBackupPoms=false"
        sh "${mavenHome}/mvn clean install"
        
        print" Building artifacts for branch $BRANCH "
	
    stage "Build Docker Images for $BRANCH"
    	timeout(time: 2, unit: 'MINUTES') {
            input "Continue with deployment?"
        }
        print"Build Docker Images for $BRANCH"
   		
    stage "Deploy JSON Schemas for $BRANCH"
   		timeout(time: 2, unit: 'MINUTES') {
            input "Continue with deployment?"
        }
        print"Deploy JSON Schemas for $BRANCH"
        
    stage "Deploy Swagger UI Documentation for $BRANCH"
		timeout(time: 2, unit: 'MINUTES') {
            input "Continue with deployment?"
        }
        print"Deploy JSON Schemas for $BRANCH"

    stage "Deploy PCP Documentation for $BRANCH"
		timeout(time: 2, unit: 'MINUTES') {
            input "Continue with deployment?"
        }
        print"Deploy JSON Schemas for $BRANCH"

    stage "Plan $ENVIRONMENT Environment Update"
    	timeout(time: 2, unit: 'MINUTES') {
            input "Continue with deployment?"
        }
        print"Plan $ENVIRONMENT Environment Update"

    stage "Update Environment $ENVIRONMENT"
   		timeout(time: 2, unit: 'MINUTES') {
            input "Continue with deployment?"
        }
        print"Updating Environment $ENVIRONMENT"

    stage "Integration Tests for $BRANCH"
    	timeout(time: 2, unit: 'MINUTES') {
            input "Continue with deployment?"
        }
        print"Running Integration Tests for $BRANCH"
   
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