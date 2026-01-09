plugins {
    java
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.google.protobuf") version "0.9.6"
}

group = "com.guildhub"
version = "0.0.1-SNAPSHOT"
description = "gRPC service for news"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

val grpcVersion = "1.63.0"
val protobufVersion = "3.25.3"
val minIOVersion = "8.5.12"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("io.minio:minio:$minIOVersion")
    implementation("io.grpc:grpc-protobuf:$grpcVersion")
    implementation("io.grpc:grpc-stub:$grpcVersion")
    implementation("io.grpc:grpc-netty-shaded:$grpcVersion")
    implementation("net.devh:grpc-server-spring-boot-starter:3.1.0.RELEASE")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.postgresql:postgresql")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    compileOnly("jakarta.annotation:jakarta.annotation-api:1.3.5")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    plugins {
        register("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
        }
    }
    generateProtoTasks {
        ofSourceSet("main").forEach { task ->
            task.plugins {
                register("grpc") {
                    option("annotation_grpc=false")
                }
            }
            task.builtins {
                named("java") {
                    option("annotate_code=false")
                }
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}