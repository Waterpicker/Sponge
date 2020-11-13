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
package org.spongepowered.common.util;


import java.util.Objects;
import java.util.function.Supplier;

public class MemoizedSupplier<T> implements Supplier<T> {

    public static <V> Supplier<V> memoize(final Supplier<V> supplier) {
        return new MemoizedSupplier<>(supplier);
    }

    final Supplier<T> delegate;
    transient volatile boolean initialized;
    transient T value;

    private MemoizedSupplier(final Supplier<T> delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public T get() {
        if (!this.initialized) {
            synchronized (this) {
                if (!this.initialized) {
                    final T t = this.delegate.get();
                    this.value = t;
                    this.initialized = true;
                    return t;
                }
            }
        }
        return this.value;
    }

}
