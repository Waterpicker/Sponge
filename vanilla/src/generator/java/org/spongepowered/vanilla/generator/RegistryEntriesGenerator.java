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
package org.spongepowered.vanilla.generator;

import com.github.javaparser.ast.body.TypeDeclaration;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;

import javax.lang.model.element.Modifier;

// Generates a constants file based on registry entries
class RegistryEntriesGenerator<V> implements Generator {

    private final String relativePackageName;
    private final String targetClassSimpleName;
    private final String registryTypeName;
    private final ResourceKey<? extends Registry<V>> registry;
    private final Predicate<V> filter;
    private final TypeName valueType;
    private final RegistryScope scopeOverride;

    RegistryEntriesGenerator(
        final String targetRelativePackage,
        final String targetClassSimpleName,
        final String registryTypeName,
        final TypeName valueType,
        final ResourceKey<? extends Registry<V>> registry
    ) {
        this(targetRelativePackage, targetClassSimpleName, registryTypeName, valueType, registry, $ -> true);
    }

    RegistryEntriesGenerator(
        final String targetRelativePackage,
        final String targetClassSimpleName,
        final String registryTypeName,
        final TypeName valueType,
        final ResourceKey<? extends Registry<V>> registry,
        final Predicate<V> filter
    ) {
        this(targetRelativePackage, targetClassSimpleName, registryTypeName, valueType, registry, filter, null);
    }

    RegistryEntriesGenerator(
        final String targetRelativePackage,
        final String targetClassSimpleName,
        final String registryTypeName,
        final TypeName valueType,
        final ResourceKey<? extends Registry<V>> registry,
        final Predicate<V> filter,
        final RegistryScope scopeOverride
    ) {
        this.relativePackageName = targetRelativePackage;
        this.targetClassSimpleName = targetClassSimpleName;
        this.valueType = valueType;
        this.registryTypeName = registryTypeName;
        this.registry = registry;
        this.filter = filter;
        this.scopeOverride = scopeOverride;
    }

    @Override
    public String name() {
        return "elements of registry " + this.registry.location();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void generate(final Context ctx) throws IOException {
        final var clazz = Types.utilityClass(
            this.targetClassSimpleName,
            "An enumeration of all possible {@link $T}s in vanilla Minecraft.",
            this.valueType instanceof ParameterizedTypeName ? ((ParameterizedTypeName) this.valueType).rawType : this.valueType
        );
        clazz.addAnnotation(Types.suppressWarnings("unused"));

        final RegistryScope scopeType;
        Registry<V> registry = ctx.registries().ownedRegistry(this.registry).orElse(null);
        if (registry == null) {
            registry = (Registry<V>) Registry.REGISTRY.get(this.registry.location());
            if (registry == null) {
                throw new IllegalArgumentException("Unknown registry " + this.registry);
            }
            scopeType = this.scopeOverride != null ? this.scopeOverride : RegistryScope.GAME;
        } else {
            scopeType = this.scopeOverride != null ? this.scopeOverride : RegistryScope.WORLD;
        }

        clazz.addAnnotation(scopeType.registryScopeAnnotation());
        final var fieldType = ParameterizedTypeName.get(scopeType.registryReferenceType(), this.valueType);
        final var factoryMethod = scopeType.registryReferenceFactory(this.registryTypeName, this.valueType);

        final Registry<V> finalRegistry = registry;
        registry.stream()
            .filter(this.filter)
            .sorted(Comparator.comparing(registry::getKey))
            .map(v -> this.makeField(this.targetClassSimpleName, fieldType, factoryMethod, finalRegistry.getKey(v)))
            .forEachOrdered(clazz::addField);

        clazz.addMethod(factoryMethod);

        ctx.write(this.relativePackageName, clazz.build());

        // Then fix up before/after comments
        final var cu = ctx.compilationUnit(this.relativePackageName, this.targetClassSimpleName);
        final TypeDeclaration<?> type = cu.getPrimaryType().get();

        final var fields = type.getFields();
        if (!fields.isEmpty()) {
            fields.get(0).setLineComment("@formatter:off");
        }

        final var constructors = type.getConstructors();
        if (!constructors.isEmpty()) {
            constructors.get(0).setLineComment("@formatter:on");
        }
    }

    private FieldSpec makeField(final String ownType, final TypeName fieldType, final MethodSpec factoryMethod, final ResourceLocation element) {
        return FieldSpec.builder(fieldType, Types.keyToFieldName(element.getPath()), Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .initializer("$L.$N($L)", ownType, factoryMethod, Types.resourceKey(element))
            .build();
    }

    enum RegistryScope {
        GAME {
            @Override
            protected CodeBlock registryKeyToReference() {
                return CodeBlock.of("asDefaultedReference(() -> $T.getGame().registries())", Types.SPONGE);
            }

            @Override
            ClassName registryReferenceType() {
                return Types.DEFAULTED_REGISTRY_REFERENCE;
            }

            @Override
            AnnotationSpec registryScopeAnnotation() {
                return RegistryScope.registryScopeAnnotation("GAME");
            }
        },
        SERVER {
            @Override
            protected CodeBlock registryKeyToReference() {
                return CodeBlock.of("asDefaultedReference(() -> $T.getServer().registries())", Types.SPONGE);
            }

            @Override
            ClassName registryReferenceType() {
                return Types.DEFAULTED_REGISTRY_REFERENCE;
            }

            @Override
            AnnotationSpec registryScopeAnnotation() {
                return RegistryScope.registryScopeAnnotation("ENGINE");
            }
        },
        WORLD {
            @Override
            protected CodeBlock registryKeyToReference() {
                return CodeBlock.of("asReference()");
            }

            @Override
            ClassName registryReferenceType() {
                return Types.REGISTRY_REFERENCE;
            }

            @Override
            AnnotationSpec registryScopeAnnotation() {
                return RegistryScope.registryScopeAnnotation("WORLD");
            }
        };

        protected static AnnotationSpec registryScopeAnnotation(final String registryScope) {
            Objects.requireNonNull(registryScope, "registryScope");
            return AnnotationSpec.builder(Types.REGISTRY_SCOPES)
                .addMember("scopes", "$T.$L", Types.REGISTRY_SCOPE, registryScope.toUpperCase(Locale.ROOT))
                .build();
        }

        protected abstract CodeBlock registryKeyToReference();

        abstract ClassName registryReferenceType();

        abstract AnnotationSpec registryScopeAnnotation();

        final MethodSpec registryReferenceFactory(String registryTypeName, TypeName valueType) {
            final var locationParam = ParameterSpec.builder(Types.RESOURCE_KEY, "location", Modifier.FINAL).build();
            return MethodSpec.methodBuilder("key")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .returns(ParameterizedTypeName.get(this.registryReferenceType(), valueType))
                .addParameter(locationParam)
                .addCode(
                    "return $T.of($T.$L, $N).$L;",
                    Types.REGISTRY_KEY,
                    Types.REGISTRY_TYPES,
                    registryTypeName.toUpperCase(Locale.ROOT),
                    locationParam,
                    this.registryKeyToReference()
                ).build();
        }
    }

}
