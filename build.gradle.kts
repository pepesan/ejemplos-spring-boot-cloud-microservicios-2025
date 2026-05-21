plugins {
    id("org.springframework.boot") version "4.0.5" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
    group = "com.cursosdedesarrollo"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }

    configure<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension> {
        imports {
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.1")
        }
    }

    dependencies {
        "compileOnly"("org.projectlombok:lombok")
        "annotationProcessor"("org.projectlombok:lombok")
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    // Por defecto Gradle ejecuta bootRun con el directorio del submódulo como working directory.
    // Fijarlo a la raíz del proyecto hace que ${user.dir} en application.yml resuelva siempre
    // a la raíz, lo que permite referenciar recursos compartidos como config-repo/ desde
    // cualquier módulo.
    tasks.withType<org.springframework.boot.gradle.tasks.run.BootRun> {
        workingDir = rootProject.projectDir
    }
}
