node {
   		//$BRANCH
		//$ENVIRONMENT
		//$CLASSIFIER
	if (BRANCH == null || BRANCH.trim().equals("")) {
        error 'No value was provided for BRANCH'
    }
		
	String mavenHome = "${tool 'M3_0_5'}/bin"
	
	stage "Build Artifacts for $BRANCH"
	
	 //Clean workspace
	   	sh 'rm -rf *'
	    sh 'rm -rf .git'
	    sh 'rm -rf .gitignore'
	    
	 //Get the Updated  project   
	 git branch: "master", poll: false, url: 'https://github.com/cristhianguardado/maven.git'
	
	
        sh "${mavenHome}/mvn versions:set -DnewVersion=${CLASSIFIER} -DgenerateBackupPoms=false"
        sh "${mavenHome}/mvn clean install"
        
        print" Building artifacts for branch $BRANCH "
	
  
   
   
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