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
package dk.nelind.loofah.launch.event;

import com.google.inject.Singleton;
import org.spongepowered.common.event.manager.SpongeEventManager;
import org.spongepowered.plugin.PluginContainer;

import java.lang.invoke.MethodHandles;

// TODO(loofah): figure out the event situation.
//  how much and what kind of event sync between loofah/sponge and fapi are we going to do?
/** Copied from {@link org.spongepowered.vanilla.launch.event.VanillaEventManager} */
@Singleton
public class FabricEventManager extends SpongeEventManager {
    private static final MethodHandles.Lookup LOOFAH_EVENTMANAGER_LOOKUP = MethodHandles.lookup();

    @Override
    public SpongeEventManager registerListeners(final PluginContainer plugin, final Object listener) {
        MethodHandles.Lookup usedLookup;
        try {
            usedLookup = MethodHandles.privateLookupIn(listener.getClass(), FabricEventManager.LOOFAH_EVENTMANAGER_LOOKUP);
        } catch (IllegalAccessException ex) {
            String errorMessage = "Loofah was unable to create a lookup for " + listener +
                " registered as an event listener by plugin " + plugin.metadata().id();
            throw new IllegalArgumentException(errorMessage, ex);
        }

        this.registerListeners(plugin, listener, usedLookup);
        return this;
    }
}
