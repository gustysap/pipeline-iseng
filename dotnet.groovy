pipeline {
    agent {
        label "master"
    }
    environment {
        NEXUS_VERSION = "nexus3"
        NEXUS_PROTOCOL = "http"
        NEXUS_URL = "172.105.119.247:8081"
        NEXUS_REPOSITORY = "repository-example"
        NEXUS_CREDENTIAL_ID = "nexus-credential"
        registry = "172.105.119.247:5000/dotnet"
        registryCredential = 'docker-nexus'
        dockerImage = ''
        dotnet = '/usr/share/dotnet/dotnet'
        VERSION = sh(script: "echo \${vars_ref} | awk -F '/' '{ print \$3 }'",returnStdout: true).trim()
    }

    stages {
        stage("clone code") {
            steps {
                script {
                //    git([url: 'http://172.105.119.247/root/hwpipe.git', branch: 'master', credentialsId: 'forgitlab'])
                checkout scm: [$class: 'GitSCM', userRemoteConfigs: [[url: 'http://172.105.119.247/gustysap/dotnet-helloworld.git', 
                credentialsId: 'gitlablogin']], branches: [[name: vars_ref ]]],poll: false
                echo VERSION
                }
            }
        }
        stage ('Restore PACKAGES') {
            steps {
                sh "dotnet restore --configfile NuGet.Config"
            }
        }
        stage('Clean') {
            steps {
                sh 'dotnet clean'
            }
        }
        stage('Build') {
            steps {
                sh 'dotnet build'
                //--configuration Release'
            }
        }
        stage('Pack') {
            steps {
                sh 'dotnet pack --no-build --output nupkgs'
            }
        }
        stage('Publish') {
            steps {
                sh "dotnet nuget push **/nupkgs/*.nupkg -k 5af67705-d4fb-392b-a147-f71fb7e3a40a -s http://172.105.119.247:8081/repository/nuget-hosted/"
            }
        }
        stage('Building image') {
         	steps{
            	script {
                //	load "env-vars/version.groovy"
                	dockerImage = docker.build registry + ":\${VERSION}"
            	}
         	}
    	}

    	stage('Deploy Image') {
        	steps{
        	    script {
					docker.withRegistry( "http://172.105.119.247:5000", registryCredential ) {
                    	dockerImage.push()
					}
				}
            }
    	}
    
		stage('Remove unused docker image') {
        	steps{
			//	load "env-vars/version.groovy"
	        	sh "docker rmi $registry:\${VERSION}"
        	}
    	}
    }
}
