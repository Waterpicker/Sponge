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
package org.spongepowered.common.event;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import org.spongepowered.api.event.cause.EventContextKey;

public final class SpongeEventContextKey<T> implements EventContextKey<T> {

    private final String id;
    private final Class<T> allowed;
    private final String name;

    SpongeEventContextKey(SpongeEventContextKeyBuilder<T> builder) {
        this.id = builder.id;
        this.allowed = builder.typeClass;
        this.name = builder.name;
    }

    public SpongeEventContextKey(String id, String name, Class<T> allowed) {
        this.id = checkNotNull(id, "Id");
        this.allowed = checkNotNull(allowed, "Allowed");
        this.name = checkNotNull(name, "Name");
    }

    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Class<T> getAllowedType() {
        return this.allowed;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("id", this.id)
            .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof EventContextKey)) {
            return false;
        }
        return this.id.equals(((EventContextKey<?>) o).getId());
    }

    @Override
    public int hashCode() {
        return this.id.hashCode();
    }

}
