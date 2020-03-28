pipeline {
    agent { label 'maven' }
    environment {
        NEXUS_VERSION = "nexus3"
        NEXUS_PROTOCOL = "http"
        NEXUS_URL = "172.105.119.247:8081"
        NEXUS_REPOSITORY = "repository-example"
        NEXUS_CREDENTIAL_ID = "nexus-credential"
        registry = "nexus.oc.indolinux.com:5050/springboot"
        registryCredential = 'docker-nexus'
        dockerImage = ''
        VERSION = sh(script: "echo \${var_ref} | awk -F '/' '{ print \$3 }'",returnStdout: true).trim()
        DB_CONNSTRING = "jdbc:postgresql://172.105.119.247:5432/demo"
        DB_USER = "heince"
        DB_PASSWORD = "itgroup"
        NEXUS_USER = "admin"
    }
    
    stages {
        //stage('Install dependencies') {
        //    steps {
        //        script {
        //            def dockerTool = tool name: 'docker', type: 'org.jenkinsci.plugins.docker.commons.tools.DockerTool'
        //            withEnv(["DOCKER=\${dockerTool}/bin"]) {
                    //stages
                    //here we can trigger: sh "sudo ${DOCKER}/docker ..."
        //            }
        //        }
        //    }
        //}
        stage("clone code") {
            steps {
                script {
                //    git([url: 'http://172.105.119.247/root/hwpipe.git', branch: 'master', credentialsId: 'forgitlab'])
                checkout scm: [$class: 'GitSCM', userRemoteConfigs: [[url: 'http://172.105.119.247/gustysap/springboot.git', 
                credentialsId: 'gitlablogin']], branches: [[name: var_ref ]]],poll: false
                def yaml = readYaml file: "templates/templates.yaml"
                echo "${yaml.name_service}"
                yaml.branch_name = var_project_default_branch
                sh "rm templates/templates.yaml"
                writeYaml file: "templates/templates.yaml", data: yaml
                echo VERSION
                echo "${yaml.branch_name}"
                }
            }
        }
        
        // stage('Test Submitter approval') {
        //agent none
        //    steps {
        //        timeout(time:1, unit:'HOURS') {
        //            input message: 'Approve?',submitter: 'test1', submitterParameter: 'approver'
        //        }
        //    }
        //}

        stage("mvn build with test") {
            steps {
                script {
                    sh "mvn clean install;mvn test"
                    sh "mvn package"
                }
            }
        }

        stage("publish to nexus") {
            steps {
                script {
                    pom = readMavenPom file: "pom.xml";
                    filesByGlob = findFiles(glob: "target/*.${pom.packaging}");
                    echo "${filesByGlob[0].name} ${filesByGlob[0].path} ${filesByGlob[0].directory} ${filesByGlob[0].length} ${filesByGlob[0].lastModified}"
                    artifactPath = filesByGlob[0].path;
                    artifactExists = fileExists artifactPath;
                    if(artifactExists) {
                        echo "*** File: ${artifactPath}, group: ${pom.groupId}, packaging: ${pom.packaging}, version ${pom.version}";
                        nexusArtifactUploader(
                            nexusVersion: NEXUS_VERSION,
                            protocol: NEXUS_PROTOCOL,
                            nexusUrl: NEXUS_URL,
                            groupId: pom.groupId,
                            version: pom.version,
                            repository: NEXUS_REPOSITORY,
                            credentialsId: NEXUS_CREDENTIAL_ID,
                            artifacts: [
                                [artifactId: pom.artifactId,
                                classifier: '',
                                file: artifactPath,
                                type: pom.packaging],
                                [artifactId: pom.artifactId,
                                classifier: '',
                                file: "pom.xml",
                                type: "pom"]
                            ]
                        );
                    } else {
                        error "*** File: ${artifactPath}, could not be found";
                    }
                }
            }
        }

		stage('Building image') {
         	steps{
         	    container('buildah') {
            	script {
                //	load "env-vars/version.groovy"
                //    dockerTool = tool name: 'docker', type: 'org.jenkinsci.plugins.docker.commons.tools.DockerTool'
                //    withEnv(["DOCKER=${dockerTool}/bin"]) {
                //    sh "\${DOCKER}/docker build -t \${registry}:\${VERSION} ."
                	sh "buildah bud -t \${registry}:\${VERSION} ."
                //	dockerImage = docker.build registry + ":\${VERSION}"
            	//}
                }
         	    }
         	}
    	}

    	stage('Deploy Image') {
        	steps{
        	container('buildah') {
        	    script {
        	        sh "podman login --username \${NEXUS_USER} --password Admin123\044%^ nexus.oc.indolinux.com:5050"
        	        //sh "buildah login -u \${NEXUS_USER} -p ${NEXUS_PASSWORD} http://172.105.119.247:5000"
        	        sh "buildah login --username \${NEXUS_USER} --password Admin123\044%^ nexus.oc.indolinux.com:5050"
        	        sh "buildah push \${registry}:\${VERSION}"
			//		docker.withRegistry( "http://172.105.119.247:5000", registryCredential ) {
            //        	dockerImage.push()
					}
				}
        	}
        	}
    
		stage('Remove unused docker image') {
        	steps{
        	    container('buildah') {
			//	load "env-vars/version.groovy"
	        	sh "buildah rmi $registry:\${VERSION}"
        	    }
        	}
    	}

		//stage('Remove unused files') {
        //	steps{
	    //    	sh "rm -rf $WORKSPACE/*"
        //	}
    	//}
    }
}
