import net.fabricmc.loom.task.RemapJarTask

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/") {
        name = "Fabric"
    }
    maven("https://maven.parchmentmc.org") {
        name = "ParchementMC"
    }
    maven("https://repo.spongepowered.org/repository/maven-public/") {
        name = "SpongePowered"
    }
}

plugins {
    id("fabric-loom") version "1.6.3"
    id("com.github.johnrengelman.shadow")
    id("implementation-structure")
    id("maven-publish")
}

val commonProject = parent!!
val apiVersion: String by project
val minecraftVersion: String by project
val fabricLoaderVersion: String by project
val fabricApiVersion: String by project
val recommendedVersion: String by project
val organization: String by project
val projectUrl: String by project

version = spongeImpl.generatePlatformBuildVersionString(apiVersion, minecraftVersion, recommendedVersion, fabricLoaderVersion)

// Common source sets and configurations
val main: SourceSet = commonProject.sourceSets.named("main").get()
val launch: SourceSet = commonProject.sourceSets.named("launch").get()
val launchConfig: Configuration = commonProject.configurations.named("launch").get()
val applaunch: SourceSet = commonProject.sourceSets.named("applaunch").get()
val mixins: SourceSet = commonProject.sourceSets.named("mixins").get()
val accessors: SourceSet = commonProject.sourceSets.named("accessors").get()


//Fabric source sets and configurations
val fabricBundledLibraries = configurations.register("bundledLibraries") {
    extendsFrom(configurations.named("minecraftLibraries").get())
    extendsFrom(configurations.named("loaderLibraries").get())
}.get()
val fabricBootstrapLibrariesConfig = configurations.register("bootstrapLibraries").get()
val fabricLibrariesConfig = configurations.register("libraries") {
    extendsFrom(fabricBootstrapLibrariesConfig)
}.get()

val fabricMain by sourceSets.named("main") {
    // implementation (compile) dependencies
    spongeImpl.applyNamedDependencyOnOutput(commonProject, mixins, this, project, this.implementationConfigurationName)
    spongeImpl.applyNamedDependencyOnOutput(commonProject, accessors, this, project, this.implementationConfigurationName)
    spongeImpl.applyNamedDependencyOnOutput(commonProject, applaunch, this, project, this.implementationConfigurationName)
    spongeImpl.applyNamedDependencyOnOutput(commonProject, launch, this, project, this.implementationConfigurationName)
    spongeImpl.applyNamedDependencyOnOutput(commonProject, main, this, project, this.implementationConfigurationName)

    configurations.named(implementationConfigurationName) {
        extendsFrom(fabricBundledLibraries)
        extendsFrom(fabricLibrariesConfig)
    }
}
val fabricLaunch by sourceSets.register("launch") {
    // implementation (compile) dependencies
    spongeImpl.applyNamedDependencyOnOutput(commonProject, launch, this, project, this.implementationConfigurationName)
    spongeImpl.applyNamedDependencyOnOutput(commonProject, applaunch, this, project, this.implementationConfigurationName)
    spongeImpl.applyNamedDependencyOnOutput(commonProject, main, this, project, this.implementationConfigurationName)
    spongeImpl.applyNamedDependencyOnOutput(project, this, fabricMain, project, fabricMain.implementationConfigurationName)

    configurations.named(implementationConfigurationName) {
        extendsFrom(fabricBundledLibraries)
        extendsFrom(fabricLibrariesConfig)
    }
}

val fabricAppLaunch by sourceSets.register("applaunch") {
    // implementation (compile) dependencies
    spongeImpl.applyNamedDependencyOnOutput(commonProject, applaunch, this, project, this.implementationConfigurationName)
    spongeImpl.applyNamedDependencyOnOutput(project, this, fabricLaunch, project, fabricLaunch.implementationConfigurationName)

    configurations.named(implementationConfigurationName) {
        extendsFrom(fabricBundledLibraries)
        extendsFrom(fabricBootstrapLibrariesConfig)
    }
}

val fabricMixins by sourceSets.register("mixins") {
    // implementation (compile) dependencies
    spongeImpl.applyNamedDependencyOnOutput(commonProject, mixins, this, project, this.implementationConfigurationName)
    spongeImpl.applyNamedDependencyOnOutput(commonProject, accessors, this, project, this.implementationConfigurationName)
    spongeImpl.applyNamedDependencyOnOutput(commonProject, applaunch, this, project, this.implementationConfigurationName)
    spongeImpl.applyNamedDependencyOnOutput(project, fabricAppLaunch, this, project, this.implementationConfigurationName)

    configurations.named(implementationConfigurationName) {
        extendsFrom(fabricBundledLibraries)
        extendsFrom(fabricBootstrapLibrariesConfig)
    }
}

configurations.configureEach {
    exclude(group = "net.minecraft", module = "joined")
    if (name != "minecraft") { // awful terrible hack sssh (directly stolen from SpongeForge cause id probably never have known how to fix this if it wasn't for SpongeForge)
        exclude(group = "com.mojang", module = "minecraft")
    }
}

loom {
    accessWidenerPath.set(file("../src/main/resources/common.accesswidener"))

    mixin {
        useLegacyMixinAp.set(false)
    }

    mods {
        register("loofah") {
            sourceSet(fabricMixins)
            sourceSet(fabricAppLaunch)
            sourceSet(fabricLaunch)
            sourceSet(fabricMain)

            sourceSet(accessors, commonProject)
            sourceSet(mixins, commonProject)
            sourceSet(applaunch, commonProject)
            sourceSet(launch, commonProject)
            sourceSet(main, commonProject)
        }
    }

    runConfigs.configureEach {
        isIdeConfigGenerated = true
    }
}

dependencies {
    val apiAdventureVersion: String by project
    val apiConfigurateVersion: String by project
    val apiGsonVersion: String by project
    val guavaVersion: String by project
    val apiPluginSpiVersion: String by project
    val jlineVersion: String by project

    //Loom minecraft config
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings(loom.layered() {
        officialMojangMappings { nameSyntheticMembers = true }
        parchment("org.parchmentmc.data:parchment-$minecraftVersion:2024.02.25")
    })
    modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")

    // Make minecraft available for the other source sets
    fabricBundledLibraries("net.minecraft:minecraft-merged-bb44d75a1e:1.20.4-loom.mappings.1_20_4.layered+hash.420342176-v2") //TODO: find something better than whatever this is
    fabricBundledLibraries("net.fabricmc:fabric-loader:$fabricLoaderVersion")

    // Mod dependencies
    //modImplementation(include("net.kyori:adventure-platform-fabric:5.12.0")!!)
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    // API dependencies
    fabricLibrariesConfig("org.spongepowered:spongeapi:$apiVersion") { isTransitive = false }
    fabricLibrariesConfig(platform(apiLibs.configurate.bom))
    fabricLibrariesConfig(apiLibs.configurate.core)
    fabricLibrariesConfig(apiLibs.configurate.hocon)
    fabricLibrariesConfig(apiLibs.configurate.gson)
    fabricLibrariesConfig(apiLibs.configurate.yaml)
    fabricLibrariesConfig(platform(apiLibs.adventure.bom))
    fabricLibrariesConfig(apiLibs.adventure.api)
    fabricLibrariesConfig(apiLibs.adventure.minimessage)
    fabricLibrariesConfig(apiLibs.adventure.textSerializer.gson)
    fabricLibrariesConfig(apiLibs.adventure.textSerializer.plain)
    fabricLibrariesConfig(apiLibs.math)
}

tasks {
    /* In case loofah specific access wideners are needed here's .... something to allow that
    //shitty hack shush the loader only supports one accesswidener file per mod
    register("mergeAccessWideners") {
        val generatedResourceDir = sourceSets.named("main").get().resources.srcDirs.first().resolve("generated")
        generatedResourceDir.mkdirs()
        val commonAccessWidenerDef = main.resources.srcDirs.first().resolve("common.accesswidener")
        commonAccessWidenerDef.createNewFile()
        val fabricAccessWidenerDef = fabricMain.resources.srcDirs.first().resolve("fabric.accesswidener")
        fabricAccessWidenerDef.createNewFile()

        generatedResourceDir
            .resolve("fabric.merged.accesswidener")
            .writeText(
                "accessWidener\tv1\tnamed\n" +
                        "\n# common.accesswidener\n" +
                        commonAccessWidenerDef.readText().substringAfter("\n") +
                        "\n# fabric.accesswidener\n" +
                        fabricAccessWidenerDef.readText().substringAfter("\n")
            )
    }*/

    processResources {
        //dependsOn(named("mergeAccessWideners"))

        inputs.property("version", project.version)

        filesMatching("fabric.mod.json") {
            expand(
                "version" to project.version
            )
        }
    }

    shadowJar {
        configurations = listOf(fabricLibrariesConfig)

        from(commonProject.sourceSets.main.map { it.output })
        from(commonProject.sourceSets.named("accessors").map {it.output })
        from(commonProject.sourceSets.named("mixins").map {it.output })
        from(commonProject.sourceSets.named("applaunch").map {it.output })
        from(commonProject.sourceSets.named("launch").map {it.output })
        from(commonProject.sourceSets.named("main").map {it.output })

        from(fabricMixins.output)
        from(fabricAppLaunch.output)
        from(fabricLaunch.output)
        from(fabricMain.output)
    }

    register("remapShadowJar", RemapJarTask::class) {
        group = "shadow"
        archiveClassifier = "mod"

        inputFile.set(shadowJar.flatMap { it.archiveFile })
    }
}

indraSpotlessLicenser {
    licenseHeaderFile(rootProject.file("HEADER.txt"))

    property("name", "Loofah")
    property("organization", "Nelind")
    property("url", "https://www.nelind.dk")
}
