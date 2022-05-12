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
package org.spongepowered.common.datapack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.leangen.geantyref.TypeToken;
import net.minecraft.core.Registry;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.spongepowered.api.advancement.Advancement;
import org.spongepowered.api.datapack.DataPackSerializable;
import org.spongepowered.api.datapack.DataPackType;
import org.spongepowered.api.item.recipe.RecipeRegistration;
import org.spongepowered.api.world.WorldTypeTemplate;
import org.spongepowered.api.world.server.WorldTemplate;
import org.spongepowered.common.bridge.world.level.dimension.LevelStemBridge;
import org.spongepowered.common.datapack.recipe.RecipeDataPackSerializer;
import org.spongepowered.common.datapack.recipe.RecipeSerializedObject;
import org.spongepowered.common.datapack.tag.TagSerializedObject;
import org.spongepowered.common.tag.SpongeTagTemplate;
import org.spongepowered.common.world.server.SpongeDimensionTypes;
import org.spongepowered.common.world.server.SpongeWorldTemplate;

import java.util.function.BiFunction;

public final class SpongeDataPackType<T extends DataPackSerializable, U extends DataPackSerializedObject> implements DataPackType<T> {

    private final TypeToken<T> token;
    private final DataPackSerializer<U> packSerializer;
    private final DataPackSerializableSerializer<T> objectSerializer;
    private final BiFunction<T, JsonObject, U> objectFunction;
    private final boolean persistent;

    public SpongeDataPackType(final TypeToken<T> token, final DataPackSerializer<U> packSerializer, final DataPackSerializableSerializer<T>
            objectSerializer, final BiFunction<T, JsonObject, U> objectFunction, final boolean persistent) {
        this.token = token;
        this.packSerializer = packSerializer;
        this.objectSerializer = objectSerializer;
        this.objectFunction = objectFunction;
        this.persistent = persistent;
    }

    @Override
    public TypeToken<T> type() {
        return this.token;
    }

    @Override
    public boolean persistent() {
        return this.persistent;
    }

    public DataPackSerializer<U> getPackSerializer() {
        return this.packSerializer;
    }

    public DataPackSerializableSerializer<T> getObjectSerializer() {
        return this.objectSerializer;
    }

    public BiFunction<T, JsonObject, U> getObjectFunction() {
        return this.objectFunction;
    }

    public static final class FactoryImpl implements DataPackType.Factory {

        private final SpongeDataPackType<@NonNull Advancement, DataPackSerializedObject> advancement = new SpongeDataPackType<>(TypeToken.get(Advancement.class),
                new DataPackSerializer<>("Advancements", "advancements"),
                (s, registryAccess) -> ((net.minecraft.advancements.Advancement) s).deconstruct().serializeToJson(),
                (i1, i2) -> new DataPackSerializedObject(i1.key(), i2),
                false
        );

        private final SpongeDataPackType<@NonNull RecipeRegistration, RecipeSerializedObject> recipe = new SpongeDataPackType<>(TypeToken.get(RecipeRegistration.class),
                new RecipeDataPackSerializer(),
                (s, registryAccess) -> ((FinishedRecipe) s).serializeRecipe(),
                (i1, i2) -> new RecipeSerializedObject(i1.key(), i2, new DataPackSerializedObject(i1.key(), ((FinishedRecipe) i1).serializeAdvancement())),
                false
        );

        private final SpongeDataPackType<@NonNull WorldTypeTemplate, DataPackSerializedObject> worldType = new SpongeDataPackType<>(TypeToken.get(WorldTypeTemplate.class),
                new DataPackSerializer<>("Dimension Types", "dimension_type"),
                s -> SpongeDimensionTypes.DIRECT_CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, BootstrapProperties.registries), (DimensionType) s.worldType()).getOrThrow(false, e -> {}),
                (i1, i2) -> new DataPackSerializedObject(i1.key(), i2),
                true
        );

        private final SpongeDataPackType<@NonNull WorldTemplate, DataPackSerializedObject> world = new SpongeDataPackType<>(TypeToken.get(WorldTemplate.class),
                new DataPackSerializer<>("Dimensions", "dimension"),
                (s, registryAccess) -> {
                    final Registry<DimensionType> dimensionTypeRegistry = registryAccess.registryOrThrow(Registry.DIMENSION_TYPE_REGISTRY);
                    final ResourceKey<DimensionType> key = ResourceKey.create(Registry.DIMENSION_TYPE_REGISTRY, (ResourceLocation) (Object) s.worldType().location());
                    final LevelStem template = new LevelStem(dimensionTypeRegistry.getHolderOrThrow(key), (ChunkGenerator) s.generator());
                    ((LevelStemBridge) (Object) template).bridge$setFromSettings(false);
                    ((LevelStemBridge) (Object) template).bridge$populateFromTemplate((SpongeWorldTemplate) s);
                    final RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registryAccess);
                    return SpongeWorldTemplate.DIRECT_CODEC.encodeStart(ops, template).getOrThrow(false, e -> {});
                },
                (i1, i2) -> new DataPackSerializedObject(i1.key(), i2),
                true
        );

        private final SpongeDataPackType<@NonNull SpongeTagTemplate, TagSerializedObject> tag = new SpongeDataPackType<@NonNull SpongeTagTemplate, TagSerializedObject>(TypeToken.get(SpongeTagTemplate.class),
                new TagDataPackSerializer("Tag", "tags"),
                (spongeTagTemplate, registryAccess) -> spongeTagTemplate.toJson(),
                (i1, i2) -> new TagSerializedObject(i1.key(), i2, i1.registryType()),
                false
        );

        @Override
        public DataPackType<RecipeRegistration> recipe() {
            return this.recipe;
        }

        @Override
        public DataPackType<Advancement> advancement() {
            return this.advancement;
        }

        @Override
        public DataPackType<WorldTypeTemplate> worldType() {
            return this.worldType;
        }

        @Override
        public DataPackType<WorldTemplate> world() {
            return world;
        }

        @Override
        public DataPackType tag() {
            return this.tag;
        }
    }
}
