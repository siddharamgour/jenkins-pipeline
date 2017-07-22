node {
    
    String branchName = "${BRANCH}"
    
   print "Hello World BRANCH : '${branchName}'"
   
   stage "print branch name"
        timeout(time: 2, unit: 'MINUTES') {
            input "Continue with deployment?"
        
        print "Branch name is : ${branchName}'"
   }
   stage "Creating $ENVIRONMENT environment " 
        timeout(time: 2, unit: 'MINUTES') {
            input "Continue with creating $ENVIRONMENT environment?"
        }
        print "$ENVIRONMENT"
   
   stage 'print classifier'
        print "$CLASSIFIER"
   
}