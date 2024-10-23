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
package dk.nelind.loofah.mixin.tracker.world.level.block;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.common.accessor.world.level.block.state.BlockBehaviourAccessor;
import org.spongepowered.common.bridge.RegistryBackedTrackableBridge;

/** Copied from {@link org.spongepowered.vanilla.mixin.tracker.world.level.block.BlocksMixin_Vanilla_Tracker} */
@Mixin(Blocks.class)
public class BlocksMixin_Fabric_Tracker {
    @Redirect(method = "<clinit>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/Block;getLootTable()Lnet/minecraft/resources/ResourceKey;"
        )
    )
    private static ResourceKey<LootTable> impl$initializeTrackerState(final Block block) {
        final boolean randomlyTicking = ((BlockBehaviourAccessor) block).invoker$isRandomlyTicking(block.defaultBlockState());

        // TODO Not the best check but the tracker options only matter during block ticks...
        if (randomlyTicking) {
            final var trackableBridge = (RegistryBackedTrackableBridge<Block>) block;
            trackableBridge.bridge$refreshTrackerStates();
        }

        return block.getLootTable();
    }
}
