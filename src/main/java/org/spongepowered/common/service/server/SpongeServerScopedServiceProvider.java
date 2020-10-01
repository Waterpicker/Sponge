/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
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
package org.spongepowered.common.service.server;

import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.spongepowered.api.Game;
import org.spongepowered.api.Server;
import org.spongepowered.api.event.Cause;
import org.spongepowered.api.event.EventContext;
import org.spongepowered.api.service.ServiceProvider;
import org.spongepowered.api.service.ban.BanService;
import org.spongepowered.api.service.economy.EconomyService;
import org.spongepowered.api.service.permission.PermissionService;
import org.spongepowered.api.service.whitelist.WhitelistService;
import org.spongepowered.common.applaunch.config.common.ServicesCategory;
import org.spongepowered.common.event.lifecycle.ProvideEngineServiceEventImpl;
import org.spongepowered.common.event.lifecycle.ProvideServiceEventImpl;
import org.spongepowered.common.service.SpongeServiceProvider;
import org.spongepowered.common.service.server.ban.SpongeBanService;
import org.spongepowered.common.service.server.permission.SpongePermissionService;
import org.spongepowered.common.service.server.whitelist.SpongeWhitelistService;
import org.spongepowered.plugin.PluginContainer;

import java.util.List;
import java.util.Optional;

public final class SpongeServerScopedServiceProvider extends SpongeServiceProvider implements ServiceProvider.ServerScoped {

    private final Server server;

    @Inject
    public SpongeServerScopedServiceProvider(final Server server, final Game game, final Injector injector) {
        super(game, injector);
        this.server = server;
    }

    @Override
    protected List<Service<?>> servicesToSelect() {
        return ImmutableList.<Service<?>>builder()
                .add(new Service<>(
                        BanService.class,
                        ServicesCategory.ServicePluginSubCategory::getBanService,
                        SpongeBanService.class))
                .add(new Service<>(
                        EconomyService.class,
                        ServicesCategory.ServicePluginSubCategory::getEconomyService,
                        null))
                .add(new Service<>(
                        PermissionService.class,
                        ServicesCategory.ServicePluginSubCategory::getPermissionService,
                        SpongePermissionService.class))
                .add(new Service<>(
                        WhitelistService.class,
                        ServicesCategory.ServicePluginSubCategory::getWhitelistService,
                        SpongeWhitelistService.class))
                .build();
    }

    @Override
    protected <T> ProvideServiceEventImpl<T> createEvent(PluginContainer container, Service<T> service) {
        return new ProvideEngineServiceEventImpl<>(Cause.of(EventContext.empty(), this.getGame()),
                this.getGame(),
                TypeToken.of(service.getServiceClass()),
                this.server
        );
    }

    @Override
    @NonNull
    public final BanService banService() {
        return this.provideUnchecked(BanService.class);
    }

    @Override
    @NonNull
    public final Optional<EconomyService> economyService() {
        return this.provide(EconomyService.class);
    }

    @Override
    @NonNull
    public final PermissionService permissionService() {
        return this.provideUnchecked(PermissionService.class);
    }

    @Override
    @NonNull
    public final WhitelistService whitelistService() {
        return this.provideUnchecked(WhitelistService.class);
    }
}