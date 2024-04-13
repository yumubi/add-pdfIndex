plugins {
    id("java")
}



//java {
////    sourceCompatibility = JavaVersion.VERSION_21
//    toolchain {
//        languageVersion.set(JavaLanguageVersion.of(21))
//    }
//}
group = "io.goji.tools"
version = "1.0-SNAPSHOT"

val projectName = "goji-tools"

val slf4jVersion = "1.7.32"
val logbackVersion = "1.2.6"



repositories {
    mavenCentral()
}

dependencies {
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}


//tasks.withType<JavaCompile> {
//    options.encoding = "UTF-8"
//    options.compilerArgs.add("--enable-preview")
//    options.compilerArgs.add("-source")
//    options.compilerArgs.add("21") // Replace with your JDK version if different
//}
//
//tasks.withType<Test> {
//    jvmArgs("--enable-preview")
//}
//
//tasks.withType<JavaExec> {
//    jvmArgs("--enable-preview")
//}
//

tasks.compileJava {
    options.encoding = "UTF-8"
    options.compilerArgs = listOf("--enable-preview")
}

//
//tasks.test {
//    useJUnitPlatform()
//}
