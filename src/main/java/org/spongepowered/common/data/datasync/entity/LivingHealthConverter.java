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
package org.spongepowered.common.data.datasync.entity;

import net.minecraft.world.entity.Entity;
import org.spongepowered.api.data.DataTransactionResult;
import org.spongepowered.api.data.Keys;
import org.spongepowered.api.data.value.Value;
import org.spongepowered.common.accessor.world.entity.LivingEntityAccessor;
import org.spongepowered.common.data.datasync.DataParameterConverter;

import java.util.Optional;

public class LivingHealthConverter extends DataParameterConverter<Float> {
    public LivingHealthConverter() {
        super(LivingEntityAccessor.accessor$DATA_HEALTH_ID());
    }

    @Override
    public Optional<DataTransactionResult> createTransaction(
        final Entity entity, final Float currentValue, final Float value
    ) {
        return Optional.of(DataTransactionResult.builder()
            .replace(Value.immutableOf(Keys.HEALTH, (double) currentValue))
            .success(Value.immutableOf(Keys.HEALTH, (double) value))
            .result(DataTransactionResult.Type.SUCCESS)
            .build());
    }

    @Override
    public Float getValueFromEvent(final Float originalValue, final DataTransactionResult result) {
        return result.successfulValue(Keys.HEALTH)
            .orElse(Value.immutableOf(Keys.HEALTH, (double) originalValue))
            .get()
            .floatValue();
    }
}