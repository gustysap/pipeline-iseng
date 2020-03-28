pipeline {
    agent { label 'maven' }
    environment {
        NEXUS_VERSION = "nexus3"
        NEXUS_PROTOCOL = "https"
        NEXUS_URL = "nexus.oc.indolinux.com"
        NEXUS_REPOSITORY = "repository-example"
        NEXUS_CREDENTIAL_ID = "nexus-credential"
        VERSION = sh(script: "echo \${var_ref} | awk -F '/' '{ print \$3 }'",returnStdout: true).trim()
    }

    stages {
        stage("clone code") {
            steps {
                script {
                //    git([url: 'http://172.105.119.247/root/hwpipe.git', branch: 'master', credentialsId: 'forgitlab'])
                checkout scm: [$class: 'GitSCM', userRemoteConfigs: [[url: 'http://172.105.119.247/gustysap/springboot.git',
                credentialsId: 'gitlablogin']], branches: [[name: var_ref ]]],poll: false
                def yaml = readYaml file: "templates/templates.yaml"
                echo "${yaml.name_service}"
                yaml.BRANCH_NAME = var_project_default_branch
                yaml.NAME_SERVICE = var_project_name
                yaml.VERSION = VERSION
                yaml.REPO_URL = var_project_http_url
                sh "rm templates/templates.yaml"
                writeYaml file: "templates/templates.yaml", data: yaml
                echo VERSION
                echo "${yaml.BRANCH_NAME}"
                }
            }
        }
        stage("mvn build") {
            steps {
                script {
                    def env = readYaml file : "templates/templates.yaml"
                    echo "${env.DB_CONNSTRING}"
                    echo "${env.DB_USER}"
                    sh "mvn clean package -Dmaven.test.skip=true"
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
        stage("deploytodev") {
            steps {
                ansibleTower(
                towerServer: 'tower',
                templateType: 'job',
                jobTemplate: 'deploytodev',
                importTowerLogs: true,
                inventory: 'todev',
                jobTags: '',
                skipJobTags: '',
                limit: '',
                removeColor: false,
                verbose: true,
                credential: 'sshforlocalhost',
                extraVars: '''---
                my_var:  "Jenkins Test"''',
                async: false
                )
            }
        }
        stage('approval For Deploy To SIT') {
            input message: 'Approve for Deploy To SIT?',
            id: 'approval'
        }
        stage("deploytosit") {
            steps {
                ansibleTower(
                towerServer: 'tower',
                templateType: 'job',
                jobTemplate: 'springboottosit',
                importTowerLogs: true,
                inventory: 'todev',
                jobTags: '',
                skipJobTags: '',
                limit: '',
                removeColor: false,
                verbose: true,
                credential: 'sshforlocalhost',
                extraVars: '''---
                my_var:  "Jenkins Test"''',
                async: false
                )
            }
        }
            
        stage('approval For Deploy To UAT') {
            input message: 'Approve for Deploy To UAT?',
            id: 'approval'
        }
        stage("deploytouat") {
            steps {
                ansibleTower(
                towerServer: 'tower',
                templateType: 'job',
                jobTemplate: 'springboottouat',
                importTowerLogs: true,
                inventory: 'todev',
                jobTags: '',
                skipJobTags: '',
                limit: '',
                removeColor: false,
                verbose: true,
                credential: 'sshforlocalhost',
                extraVars: '''---
                my_var:  "Jenkins Test"''',
                async: false
                )
            }
        }
    }
}
