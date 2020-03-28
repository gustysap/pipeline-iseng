pipeline {
    agent { label 'maven' }
    environment {
        NEXUS_VERSION = "nexus3"
        NEXUS_PROTOCOL = "https"
        NEXUS_URL = "nexus.oc.indolinux.com"
        NEXUS_REPOSITORY = "\${var_project_name}"
        NEXUS_CREDENTIAL_ID = "nexus-credential"
        NAMA_APLIKASI = sh(script: "echo \${var_project_name} | awk -F '-' '{ print \$1 }'",returnStdout:true).trim()
        NAMA_MICROSERVICE = sh(script: "echo \${var_project_name} | awk -F '-' '{ print \$2 }'",returnStdout:true).trim()
        VERSION = sh(script: "echo \${var_ref} | awk -F '/' '{ print \$3 }'",returnStdout: true).trim()
    }

    stages {
        stage("clone code") {
            steps {
                script {
                //    git([url: 'http://172.105.119.247/root/hwpipe.git', branch: 'master', credentialsId: 'forgitlab'])
                checkout scm: [$class: 'GitSCM', userRemoteConfigs: [[url: var_project_http_url,
                credentialsId: 'gitlablogin']], branches: [[name: var_ref ]]],poll: false
                //def json = readJSON file: 'templates/templates.json'
                //assert json.environment
                //echo "${json.environment}"
                //json.each {
                //        key, value -> println("${key} = ${value}");
                //}
                //json.each {
                //        key, value -> 
                //        "${key}" = "${value}"
                //        echo "${name_repo_scm}"
                //}
                //echo "${json.environment.namakey}"
                //echo "${json.environment.namavalue}"
                //json.environment.namakey = json.environment.namavalue
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
                    configFileProvider([configFile(fileId: 'MAVEN_CONFIG', variable: 'MAVEN_GLOBAL_SETTINGS')]) {
                    sh "mvn -gs $MAVEN_GLOBAL_SETTINGS clean package -Dmaven.test.skip=true"
                    }
                }
            }
        }
        stage("mvn test") {
            steps {
                script {
                    def json = readJSON file: 'templates/templates.json'
                    sh """
                    export ${json.environment_variabel.nama_key1}=${json.environment_variabel.nama_value1}
                    export ${json.environment_variabel.nama_key2}=${json.environment_variabel.nama_value2}
                    export ${json.environment_variabel.nama_key3}=${json.environment_variabel.nama_value3}
                    mvn clean test"""
                    }
                }
            }

        stage("publish to nexus") {
            steps {
                script {
                    pom = readMavenPom file: "pom.xml";
                    filesByGlob = findFiles(glob: "target/*.${pom.packaging}");
                    //echo "${filesByGlob[0].name} ${filesByGlob[0].path} ${filesByGlob[0].directory} ${filesByGlob[0].length} ${filesByGlob[0].lastModified}"
                    namefile = "${var_project_name}";
                    sh "cp ${filesByGlob[0].path} ${namefile}.jar";
                    artifactPath = "${namefile}.jar";
                    artifactExists = fileExists artifactPath;
                    if(artifactExists) {
                    //    echo "*** File: ${artifactPath}, group: ${pom.groupId}, packaging: ${pom.packaging}, version ${pom.version}";
                        nexusArtifactUploader(
                            nexusVersion: NEXUS_VERSION,
                            protocol: NEXUS_PROTOCOL,
                            nexusUrl: NEXUS_URL,
                            groupId: pom.groupId,
                            version: VERSION,
                            repository: NEXUS_REPOSITORY,
                            credentialsId: NEXUS_CREDENTIAL_ID,
                            artifacts: [
                                [artifactId: namefile,
                                classifier: '',
                                file: artifactPath,
                                type: pom.packaging],
                                [artifactId: namefile,
                                classifier: '',
                                file: "pom.xml",
                                type: "pom"],
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
                script {
                def pom = readMavenPom file: "pom.xml"
                def repopath = "${pom.groupId}".replace(".", "/") + "/${var_project_name}/${VERSION}"
                def json = readJSON file: 'templates/templates.json'
                ansibleTower(
                towerServer: 'tower',
                templateType: 'job',
                jobTemplate: 'deploytoocp',
                importTowerLogs: true,
                inventory: 'todev',
                jobTags: '',
                skipJobTags: '',
                limit: '',
                removeColor: false,
                verbose: true,
                credential: 'sshforlocalhost',
                extraVars: """---
                nama_aplikasi:  "${var_project_name}"
                nama_aplikasiproject:  "${NAMA_APLIKASI}"
                nama_image: "${json.nama_image}"
                nama_artifact: "${var_project_name}-${VERSION}.jar"
                env_dev: "dev"
                VERSION: "${VERSION}"
                expose_service: "${json.expose_service}"
                port_expose: "${json.port_expose}"
                artifacturl: '${NEXUS_PROTOCOL}://${NEXUS_URL}/repository/${NEXUS_REPOSITORY}/${repopath}/${var_project_name}-${VERSION}.jar'
                namakey1: "${json.environment_variabel.nama_key1}"
                namakey2: "${json.environment_variabel.nama_key2}"
                namakey3: "${json.environment_variabel.nama_key3}"
                namavalue1: "${json.environment_variabel.nama_value1}"
                namavalue2: "${json.environment_variabel.nama_value2}"
                namavalue3: "${json.environment_variabel.nama_value3}"
                service_account: "ocbc"
                """
                )
                }
            }
        post {
        failure {
            mail to: 'aria40nanda@gmail.com, gusty.prakoso@itgroupinc.asia',
            subject: """The Pipeline Failed : ${currentBuild.fullDisplayName}""", 
            body : """FAILED: Job ${env.JOB_NAME} [${env.BUILD_NUMBER}]
         Check console output at ${env.BUILD_URL} ${env.JOB_NAME} [${env.BUILD_NUMBER}]"""
        }
        success {
             mail to: 'aria40nanda@gmail.com, ibnal.affan@itgroupinc.asia, gusty.prakoso@itgroupinc.asia',
            subject: """The Pipeline ${currentBuild.fullDisplayName} Deploy to DEV Success""", 
            body : 'Mohon Approval Untuk Step Selanjutnya , Login @ https://jenkins-cicd.apps.cluster-e6d5.e6d5.sandbox506.opentlc.com/', 
            from : 'Administrator@jenkins-ci.cd'
            }
        }
        }
        stage("approval For Deploy To SIT") {
            steps {
                input message: 'Approve for Deploy To SIT?',
                id: 'approval'
            }
        }
        stage("deploytoSIT") {
            steps {
                script {
                def pom = readMavenPom file: "pom.xml"
                def repopath = "${pom.groupId}".replace(".", "/") + "/${var_project_name}/${VERSION}"
                def json = readJSON file: 'templates/templates.json'
                ansibleTower(
                towerServer: 'tower',
                templateType: 'job',
                jobTemplate: 'deploytoocp',
                importTowerLogs: true,
                inventory: 'todev',
                jobTags: '',
                skipJobTags: '',
                limit: '',
                removeColor: false,
                verbose: true,
                credential: 'sshforlocalhost',
                extraVars: """---
                nama_aplikasi:  "${var_project_name}"
                nama_aplikasiproject:  "${NAMA_APLIKASI}"
                nama_image: "${json.nama_image}"
                nama_artifact: "${var_project_name}-${VERSION}.jar"
                env_dev: "sit"
                VERSION: "${VERSION}"
                expose_service: "${json.expose_service}"
                port_expose: "${json.port_expose}"
                artifacturl: '${NEXUS_PROTOCOL}://${NEXUS_URL}/repository/${NEXUS_REPOSITORY}/${repopath}/${var_project_name}-${VERSION}.jar'
                namakey1: "${json.environment_variabel.nama_key1}"
                namakey2: "${json.environment_variabel.nama_key2}"
                namakey3: "${json.environment_variabel.nama_key3}"
                namavalue1: "${json.environment_variabel.nama_value1}"
                namavalue2: "${json.environment_variabel.nama_value2}"
                namavalue3: "${json.environment_variabel.nama_value3}"
                service_account: "ocbc"
                """
                )
                }
            }
        post {
        failure {
            mail to: 'aria40nanda@gmail.com, gusty.prakoso@itgroupinc.asia',
            subject: """The Pipeline Failed : ${currentBuild.fullDisplayName}""", 
            body : """FAILED: Job ${env.JOB_NAME} [${env.BUILD_NUMBER}]
         Check console output at ${env.BUILD_URL} ${env.JOB_NAME} [${env.BUILD_NUMBER}]"""
        }
        success {
             mail to: 'aria40nanda@gmail.com, ibnal.affan@itgroupinc.asia, gusty.prakoso@itgroupinc.asia',
            subject: """The Pipeline ${currentBuild.fullDisplayName} Deploy to SIT Success""", 
            body : 'Mohon Approval Untuk Step Selanjutnya , Login @ https://jenkins-cicd.apps.cluster-e6d5.e6d5.sandbox506.opentlc.com/', 
            from : 'Administrator@jenkins-ci.cd'
            }
        }
        }
            
        stage("approval For Deploy To UAT") {
            steps {
                input message: 'Approve for Deploy To UAT?',
                id: 'approval'
            }
        }
            stage("deploytouat") {
            steps {
                script {
                def pom = readMavenPom file: "pom.xml"
                def repopath = "${pom.groupId}".replace(".", "/") + "/${var_project_name}/${VERSION}"
                def json = readJSON file: 'templates/templates.json'
                ansibleTower(
                towerServer: 'tower',
                templateType: 'job',
                jobTemplate: 'deploytoocp',
                importTowerLogs: true,
                inventory: 'todev',
                jobTags: '',
                skipJobTags: '',
                limit: '',
                removeColor: false,
                verbose: true,
                credential: 'sshforlocalhost',
                extraVars: """---
                nama_aplikasi:  "${var_project_name}"
                nama_aplikasiproject:  "${NAMA_APLIKASI}"
                nama_image: "${json.nama_image}"
                nama_artifact: "${var_project_name}-${VERSION}.jar"
                env_dev: "uat"
                VERSION: "${VERSION}"
                expose_service: "${json.expose_service}"
                port_expose: "${json.port_expose}"
                artifacturl: '${NEXUS_PROTOCOL}://${NEXUS_URL}/repository/${NEXUS_REPOSITORY}/${repopath}/${var_project_name}-${VERSION}.jar'
                namakey1: "${json.environment_variabel.nama_key1}"
                namakey2: "${json.environment_variabel.nama_key2}"
                namakey3: "${json.environment_variabel.nama_key3}"
                namavalue1: "${json.environment_variabel.nama_value1}"
                namavalue2: "${json.environment_variabel.nama_value2}"
                namavalue3: "${json.environment_variabel.nama_value3}"
                service_account: "ocbc"
                """
                )
                }
            }
        post {
        failure {
            mail to: 'aria40nanda@gmail.com, gusty.prakoso@itgroupinc.asia',
            subject: """The Pipeline Failed : ${currentBuild.fullDisplayName}""", 
            body : """FAILED: Job ${env.JOB_NAME} [${env.BUILD_NUMBER}]
         Check console output at ${env.BUILD_URL} ${env.JOB_NAME} [${env.BUILD_NUMBER}]"""
            }
        }
    }
        stage ('Post Notification') {
        steps {
            script {
            def json = readJSON file: 'templates/templates.json'
            emailext (
            to: 'aria40nanda@gmail.com, gusty.prakoso@itgroupinc.asia, ibnal.affan@itgroupinc.asia', 
            subject: """The Pipeline ${currentBuild.fullDisplayName} Deploy to UAT Success""", 
            body : """Mohon untuk step selanjutnya Deploy ke Production dengan
            nama aplikasi = ${NAMA_APLIKASI}
            nama microservice = ${NAMA_MICROSERVICE}
            baseimage = ${json.nama_image}
            port yang diexpose = ${json.port_expose}
            environment variabel= 
            ${json.environment_variabel.nama_key1}=${json.environment_variabel.nama_value1}
            ${json.environment_variabel.nama_key2}=${json.environment_variabel.nama_value2}
            ${json.environment_variabel.nama_key3}=${json.environment_variabel.nama_value3}
            """
            )
            }
        }
        }
    }
}
