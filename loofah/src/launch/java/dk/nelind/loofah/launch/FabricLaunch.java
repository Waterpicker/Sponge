/*
 * This file is part of Loofah, licensed under the MIT License (MIT).
 *
 * Copyright (c) Nelind <https://www.nelind.dk>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package dk.nelind.loofah.launch;

import com.google.common.collect.Lists;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;
import dk.nelind.loofah.applaunch.plugin.FabricPluginPlatform;
import dk.nelind.loofah.launch.inject.FabricModule;
import dk.nelind.loofah.launch.mapping.FabricMappingManager;
import dk.nelind.loofah.launch.plugin.FabricDummyPluginContainer;
import dk.nelind.loofah.launch.plugin.FabricPluginManager;
import dk.nelind.loofah.launch.plugin.modbacked.FabricModBackedPluginContainer;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.SharedConstants;
import org.spongepowered.common.SpongeCommon;
import org.spongepowered.common.SpongeLifecycle;
import org.spongepowered.common.applaunch.plugin.PluginPlatform;
import org.spongepowered.common.inject.SpongeCommonModule;
import org.spongepowered.common.inject.SpongeGuice;
import org.spongepowered.common.inject.SpongeModule;
import org.spongepowered.common.launch.Launch;
import org.spongepowered.plugin.PluginContainer;

import java.util.Collection;
import java.util.List;

public class FabricLaunch extends Launch {
    private final FabricMappingManager mappingManager;
    private final FabricPluginManager pluginManager;

    public FabricLaunch(PluginPlatform pluginPlatform) {
        super(pluginPlatform);
        this.mappingManager = new FabricMappingManager();
        this.pluginManager = new FabricPluginManager();
    }

    public static void launch(FabricPluginPlatform pluginPlatform) {
        FabricLaunch launch = new FabricLaunch(pluginPlatform);
        Launch.setInstance(launch);
        launch.bootstrap();
    }

    private void bootstrap() {
        this.createPlatformPlugins();

        // SpongeCommon bootstrap based on org.spongepowered.forge.mixin.core.server.BootstrapMixin_Forge
        // and org.spongepowered.vanilla.launch.VanillaBootstrap
        SharedConstants.tryDetectVersion();
        final Stage stage = SpongeGuice.getInjectorStage(this.injectionStage());
        SpongeCommon.logger().debug("Creating injector in stage '{}'", stage);
        final Injector bootstrapInjector = this.createInjector();
        final SpongeLifecycle lifecycle = bootstrapInjector.getInstance(SpongeLifecycle.class);
        this.setLifecycle(lifecycle);
        lifecycle.establishFactories();
        lifecycle.establishBuilders();

        this.logger().info("Loading Plugins");
        this.pluginManager.loadPlugins(this.pluginPlatform());
    }

    private void createPlatformPlugins() {
        final FabricLoader loader = FabricLoader.getInstance();
        ModContainer minecraftMod = loader.getModContainer("minecraft")
            .orElseThrow(() -> new IllegalStateException("Tried to get the Minecraft ModContainer, " +
                "but it wasn't available. This should be impossible!!"));
        ModContainer fabricLoaderMod = loader.getModContainer("fabricloader")
            .orElseThrow(() -> new IllegalStateException("Tried to get the Fabric loader ModContainer, " +
                "but it wasn't available. This should be impossible!!"));
        ModContainer fabricApiMod = loader.getModContainer("fabric-api")
            .orElseThrow(() -> new IllegalStateException("Tried to get the Fabric API ModContainer ," +
                "but it wasn't available. This should be impossible!!"));
        ModContainer loofahMod = loader.getModContainer("loofah")
            .orElseThrow(() -> new IllegalStateException("Tried to get own ModContainer, " +
                "but it wasn't available. This should be impossible!!"));

        this.pluginManager.addPlugin(FabricModBackedPluginContainer.of(minecraftMod));
        this.pluginManager.addPlugin(FabricModBackedPluginContainer.of(fabricLoaderMod));
        this.pluginManager.addPlugin(FabricModBackedPluginContainer.of(fabricApiMod));
        // Get SpongeCommon and SpongeAPI plugins from the mod jar
        // since they can't be loom included for a variety of reasons
        this.pluginPlatform().getCandidates().values().stream().flatMap(Collection::stream)
            .filter(plugin -> List.of("spongeapi", "sponge").contains(plugin.metadata().id()))
            .map(FabricDummyPluginContainer::of)
            .forEach(this.pluginManager()::addPlugin);
        this.pluginManager.addPlugin(FabricModBackedPluginContainer.of(loofahMod));
    }

    @Override
    public Stage injectionStage() {
        return FabricLoader.getInstance().isDevelopmentEnvironment() ? Stage.DEVELOPMENT : Stage.PRODUCTION;
    }

    @Override
    public boolean dedicatedServer() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER;
    }

    @Override
    public FabricMappingManager mappingManager() {
        return this.mappingManager;
    }

    @Override
    public FabricPluginManager pluginManager() {
        return this.pluginManager;
    }

    @Override
    public FabricPluginPlatform pluginPlatform() {
        return ((FabricPluginPlatform) this.pluginPlatform);
    }

    @Override
    public PluginContainer platformPlugin() {
        return this.pluginManager.plugin("loofah").orElseThrow( () -> new IllegalStateException("Couldn't get the " +
            "plugin representing Loofah this shouldn't be possible!"));
    }

    @Override
    public Injector createInjector() {
        final List<Module> modules = Lists.newArrayList(
            new SpongeModule(),
            new SpongeCommonModule(),
            new FabricModule()
        );
        return Guice.createInjector(this.injectionStage(), modules);
    }
}
