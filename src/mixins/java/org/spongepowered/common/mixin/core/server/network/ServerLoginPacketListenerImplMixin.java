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
package org.spongepowered.common.mixin.core.server.network;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.DefaultUncaughtExceptionHandler;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import net.minecraft.network.protocol.login.ServerboundKeyPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.Crypt;
import net.minecraft.util.CryptException;
import org.apache.commons.lang3.Validate;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.event.Cause;
import org.spongepowered.api.event.EventContext;
import org.spongepowered.api.event.SpongeEventFactory;
import org.spongepowered.api.event.network.ServerSideConnectionEvent;
import org.spongepowered.api.network.EngineConnection;
import org.spongepowered.api.network.EngineConnectionState;
import org.spongepowered.api.network.ServerSideConnection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.common.SpongeCommon;
import org.spongepowered.common.adventure.SpongeAdventure;
import org.spongepowered.common.bridge.network.ConnectionBridge;
import org.spongepowered.common.bridge.network.ServerLoginPacketListenerImplBridge;
import org.spongepowered.common.bridge.server.players.PlayerListBridge;
import org.spongepowered.common.network.SpongeEngineConnection;
import org.spongepowered.common.network.channel.ConnectionUtil;
import org.spongepowered.common.network.channel.SpongeChannelAnswerPayload;
import org.spongepowered.common.network.channel.SpongeChannelManager;
import org.spongepowered.common.network.channel.TransactionStore;
import org.spongepowered.common.profile.SpongeGameProfile;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.PrivateKey;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;

@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class ServerLoginPacketListenerImplMixin implements ServerLoginPacketListenerImplBridge {

    // @formatter:off
    @Shadow @Final static Logger LOGGER;

    @Shadow @Final Connection connection;
    @Shadow private com.mojang.authlib.GameProfile authenticatedProfile;
    @Shadow @Final MinecraftServer server;
    @Shadow private ServerLoginPacketListenerImpl.State state;
    @Shadow @Final private byte[] challenge;
    @Shadow @Nullable String requestedUsername;

    @Shadow public abstract void shadow$disconnect(Component reason);
    @Shadow abstract void shadow$startClientVerification(GameProfile profile);
    // @formatter:on

    private static final ExecutorService impl$EXECUTOR = Executors.newCachedThreadPool(new ThreadFactoryBuilder()
            .setNameFormat("Sponge-LoginThread-%d")
            .setDaemon(true)
            .setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(LOGGER))
            .build());

    private static final int NEGOTIATION_NOT_STARTED = 0;
    private static final int NEGOTIATION_INTENT = 1;
    private static final int NEGOTIATION_HANDSHAKE = 2;

    private static final int INTENT_SYNC_PLUGIN_DATA = 0;
    private static final int INTENT_DONE = 1;

    // Handshake state:
    // 1. Sync registered plugin channels
    // 2. Post handshake event and plugins can start sending login payloads
    // 3. Wait until the client responded for each of the plugin' requests
    private static final int HANDSHAKE_NOT_STARTED = 0;
    private static final int HANDSHAKE_CLIENT_TYPE = 1;
    private static final int HANDSHAKE_SYNC_CHANNEL_REGISTRATIONS = 2;
    private static final int HANDSHAKE_CHANNEL_REGISTRATION = 3;
    private static final int HANDSHAKE_SYNC_PLUGIN_DATA = 4;
    private static final int HANDSHAKE_DONE = 5;

    //Packed long. Avoids subtle threading related problems with
    //two separate fields.
    //Bits 63-32 are for the phase.
    //Bits 31-0 are for the state.
    private volatile long impl$negotiationValue = ServerLoginPacketListenerImplMixin.impl$packNegotiationValue(
        ServerLoginPacketListenerImplMixin.NEGOTIATION_NOT_STARTED, 0);

    @Override
    public Connection bridge$getConnection() {
        return this.connection;
    }

    @Redirect(method = "verifyLoginAndFinishConnectionSetup",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;canPlayerLogin(Ljava/net/SocketAddress;Lcom/mojang/authlib/GameProfile;)Lnet/minecraft/network/chat/Component;"))
    private Component impl$onCanPlayerLogin(final PlayerList instance, final SocketAddress $$0, final GameProfile $$1) {
        //We check this as part of auth event.
        return null;
    }

    private void impl$disconnectClient(final net.kyori.adventure.text.Component disconnectMessage) {
        final Component reason = SpongeAdventure.asVanilla(disconnectMessage);
        this.shadow$disconnect(reason);
    }

    @Inject(method = "startClientVerification(Lcom/mojang/authlib/GameProfile;)V",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/server/network/ServerLoginPacketListenerImpl;state:Lnet/minecraft/server/network/ServerLoginPacketListenerImpl$State;"),
            cancellable = true)
    private void impl$handleAuthEventCancellation(final CallbackInfo ci) {
        ci.cancel();

        final ServerSideConnection connection = (ServerSideConnection) ((ConnectionBridge) this.connection).bridge$getEngineConnection();
        final TransactionStore store = ConnectionUtil.getTransactionStore(connection);
        if (store.isEmpty()) {
            this.impl$processVerification();
        } else {
            this.impl$changeNegotiationPhase(ServerLoginPacketListenerImplMixin.NEGOTIATION_INTENT, ServerLoginPacketListenerImplMixin.INTENT_SYNC_PLUGIN_DATA);
            this.state = ServerLoginPacketListenerImpl.State.NEGOTIATING;
        }
    }

    private void impl$processVerification() {
        ((PlayerListBridge) this.server.getPlayerList()).bridge$canPlayerLogin(this.connection.getRemoteAddress(), this.authenticatedProfile)
            .handle((componentOpt, throwable) -> {
                if (throwable != null) {
                    // An error occurred during login checks so we ask to abort.
                    ((ConnectionBridge) this.connection).bridge$setKickReason(Component.literal("An error occurred checking ban/whitelist status."));
                    SpongeCommon.logger().error("An error occurred when checking the ban/whitelist status of {}.", this.authenticatedProfile.getId().toString());
                    SpongeCommon.logger().error(throwable);
                } else if (componentOpt != null) {
                    // We handle this later
                    ((ConnectionBridge) this.connection).bridge$setKickReason(componentOpt);
                }
                return null;
            }).handleAsync((ignored, throwable) -> {
                if (throwable != null) {
                    // We're just going to disconnect here, because something went horribly wrong.
                    if (throwable instanceof CompletionException) {
                        throw (CompletionException) throwable;
                    } else {
                        throw new CompletionException(throwable);
                    }
                }
                this.impl$fireAuthEvent();
                return null;
            }, ServerLoginPacketListenerImplMixin.impl$EXECUTOR).exceptionally(throwable -> {
                SpongeCommon.logger().error("Forcibly disconnecting user {}({}) due to an error during login.", this.authenticatedProfile.getName(), this.authenticatedProfile.getId(), throwable);
                this.shadow$disconnect(Component.literal("Internal Server Error: unable to complete login."));
                return null;
            });
    }

    private void impl$fireAuthEvent() {
        final @Nullable Component kickReason = ((ConnectionBridge) this.connection).bridge$getKickReason();
        final net.kyori.adventure.text.Component disconnectMessage;
        if (kickReason != null) {
            disconnectMessage = SpongeAdventure.asAdventure(kickReason);
        } else {
            disconnectMessage = net.kyori.adventure.text.Component.text("You are not allowed to log in to this server.");
        }
        final SpongeEngineConnection connection = ((ConnectionBridge) this.connection).bridge$getEngineConnection();
        connection.setGameProfile(this.authenticatedProfile);
        final Cause cause = Cause.of(EventContext.empty(), this);
        final ServerSideConnectionEvent.Auth event = SpongeEventFactory.createServerSideConnectionEventAuth(
                cause, disconnectMessage, disconnectMessage, (ServerSideConnection) connection,
                SpongeGameProfile.of(this.authenticatedProfile));
        if (kickReason != null) {
            event.setCancelled(true);
        }
        if (connection.postGuardedEvent(event)) {
            this.impl$disconnectClient(event.message());
            return;
        }

        this.impl$changeNegotiationPhase(ServerLoginPacketListenerImplMixin.NEGOTIATION_HANDSHAKE, ServerLoginPacketListenerImplMixin.HANDSHAKE_NOT_STARTED);
        this.state = ServerLoginPacketListenerImpl.State.NEGOTIATING;
    }

    /**
     * @author aromaa
     * @reason Use thread pool
     */
    @Overwrite
    public void handleKey(final ServerboundKeyPacket packet) {
        Validate.validState(this.state == ServerLoginPacketListenerImpl.State.KEY, "Unexpected key packet");

        final String $$5;
        try {
            final PrivateKey $$1 = this.server.getKeyPair().getPrivate();
            if (!packet.isChallengeValid(this.challenge, $$1)) {
                throw new IllegalStateException("Protocol error");
            }

            final SecretKey $$2 = packet.getSecretKey($$1);
            final Cipher $$3 = Crypt.getCipher(2, $$2);
            final Cipher $$4 = Crypt.getCipher(1, $$2);
            $$5 = new BigInteger(Crypt.digestData("", this.server.getKeyPair().getPublic(), $$2)).toString(16);
            this.state = ServerLoginPacketListenerImpl.State.AUTHENTICATING;
            this.connection.setEncryptionKey($$3, $$4);
        } catch (CryptException var7) {
            throw new IllegalStateException("Protocol error", var7);
        }

        //Sponge start
        ServerLoginPacketListenerImplMixin.impl$EXECUTOR.submit(() -> {
            //Sponge end
            final String username = Objects.requireNonNull(this.requestedUsername, "Player name not initialized");

            try {
                final ProfileResult $$1 = ServerLoginPacketListenerImplMixin.this.server.getSessionService().hasJoinedServer(username, $$5, this.impl$getAddress());
                if ($$1 != null) {
                    final GameProfile $$2 = $$1.profile();
                    ServerLoginPacketListenerImplMixin.LOGGER.info("UUID of player {} is {}", $$2.getName(), $$2.getId());
                    ServerLoginPacketListenerImplMixin.this.shadow$startClientVerification($$2);
                } else if (ServerLoginPacketListenerImplMixin.this.server.isSingleplayer()) {
                    ServerLoginPacketListenerImplMixin.LOGGER.warn("Failed to verify username but will let them in anyway!");
                    ServerLoginPacketListenerImplMixin.this.shadow$startClientVerification(UUIDUtil.createOfflineProfile(username));
                } else {
                    ServerLoginPacketListenerImplMixin.this.shadow$disconnect(Component.translatable("multiplayer.disconnect.unverified_username"));
                    ServerLoginPacketListenerImplMixin.LOGGER.error("Username '{}' tried to join with an invalid session", username);
                }
            } catch (AuthenticationUnavailableException var4) {
                if (ServerLoginPacketListenerImplMixin.this.server.isSingleplayer()) {
                    ServerLoginPacketListenerImplMixin.LOGGER.warn("Authentication servers are down but will let them in anyway!");
                    ServerLoginPacketListenerImplMixin.this.shadow$startClientVerification(UUIDUtil.createOfflineProfile(username));
                } else {
                    ServerLoginPacketListenerImplMixin.this.shadow$disconnect(Component.translatable("multiplayer.disconnect.authservers_down"));
                    ServerLoginPacketListenerImplMixin.LOGGER.error("Couldn't verify username because servers are unavailable");
                }
            }
        });
    }

    @Nullable
    private InetAddress impl$getAddress() {
        SocketAddress $$0 = this.connection.getRemoteAddress();
        return this.server.getPreventProxyConnections() && $$0 instanceof InetSocketAddress
                ? ((InetSocketAddress)$$0).getAddress()
                : null;
    }

    @Inject(method = "handleCustomQueryPacket", at = @At("HEAD"), cancellable = true)
    private void impl$onHandleCustomQueryPacket(final ServerboundCustomQueryAnswerPacket packet, final CallbackInfo ci) {
        if (!(packet.payload() instanceof final SpongeChannelAnswerPayload payload)) {
            return;
        }

        ci.cancel();

        this.server.execute(() -> {
            final SpongeChannelManager channelRegistry = (SpongeChannelManager) Sponge.channelManager();
            final EngineConnection connection = ((ConnectionBridge) this.connection).bridge$getEngineConnection();
            channelRegistry.handleLoginResponsePayload(connection, (EngineConnectionState) this, payload.id(), packet.transactionId(), payload.consumer());
        });
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void impl$onTick(final CallbackInfo ci) {
        if (this.state == ServerLoginPacketListenerImpl.State.NEGOTIATING) {
            final long value = this.impl$negotiationValue;
            final int phase = ServerLoginPacketListenerImplMixin.impl$readNegotiationPhase(value);
            final int state = ServerLoginPacketListenerImplMixin.impl$readNegotiationState(value);
            if (phase == ServerLoginPacketListenerImplMixin.NEGOTIATION_INTENT) {
                this.impl$handleIntentNegotiation(state);
            } else if (phase == ServerLoginPacketListenerImplMixin.NEGOTIATION_HANDSHAKE) {
                this.impl$handleHandshakeNegotiation(state);
            }
        }
    }

    private void impl$handleIntentNegotiation(final int state) {
        if (state == ServerLoginPacketListenerImplMixin.INTENT_SYNC_PLUGIN_DATA) {
            final ServerSideConnection connection = (ServerSideConnection) ((ConnectionBridge) this.connection).bridge$getEngineConnection();
            final TransactionStore store = ConnectionUtil.getTransactionStore(connection);
            if (store.isEmpty()) {
                this.impl$changeNegotiationState(ServerLoginPacketListenerImplMixin.INTENT_DONE);
                this.impl$processVerification();
            }
        }
    }

    private void impl$handleHandshakeNegotiation(final int state) {
        final ServerSideConnection connection = (ServerSideConnection) ((ConnectionBridge) this.connection).bridge$getEngineConnection();
        if (state == ServerLoginPacketListenerImplMixin.HANDSHAKE_NOT_STARTED) {
            this.impl$changeNegotiationState(ServerLoginPacketListenerImplMixin.HANDSHAKE_CLIENT_TYPE);

            ((SpongeChannelManager) Sponge.channelManager()).requestClientType(connection).thenAccept(result -> {
                this.impl$changeNegotiationState(ServerLoginPacketListenerImplMixin.HANDSHAKE_SYNC_CHANNEL_REGISTRATIONS);
            });

        } else if (state == ServerLoginPacketListenerImplMixin.HANDSHAKE_SYNC_CHANNEL_REGISTRATIONS) {
            this.impl$changeNegotiationState(ServerLoginPacketListenerImplMixin.HANDSHAKE_CHANNEL_REGISTRATION);

            ((SpongeChannelManager) Sponge.channelManager()).sendLoginChannelRegistry(connection).thenAccept(result -> {
                final Cause cause = Cause.of(EventContext.empty(), this);
                final ServerSideConnectionEvent.Handshake event =
                    SpongeEventFactory.createServerSideConnectionEventHandshake(cause, connection, SpongeGameProfile.of(this.authenticatedProfile));
                SpongeCommon.post(event);
                this.impl$changeNegotiationState(ServerLoginPacketListenerImplMixin.HANDSHAKE_SYNC_PLUGIN_DATA);
            });
        } else if (state == ServerLoginPacketListenerImplMixin.HANDSHAKE_SYNC_PLUGIN_DATA) {
            final TransactionStore store = ConnectionUtil.getTransactionStore(connection);
            if (store.isEmpty()) {
                this.impl$changeNegotiationState(ServerLoginPacketListenerImplMixin.HANDSHAKE_DONE);
                this.state = ServerLoginPacketListenerImpl.State.VERIFYING;
            }
        }
    }

    @Override
    public boolean bridge$isIntentDone() {
        final int phase = ServerLoginPacketListenerImplMixin.impl$readNegotiationPhase(this.impl$negotiationValue);
        return phase > ServerLoginPacketListenerImplMixin.NEGOTIATION_INTENT;
    }

    private void impl$changeNegotiationPhase(final int phase, final int state) {
        this.impl$negotiationValue = ServerLoginPacketListenerImplMixin.impl$packNegotiationValue(phase, state);
    }

    private void impl$changeNegotiationState(final int state) {
        this.impl$changeNegotiationPhase(ServerLoginPacketListenerImplMixin.impl$readNegotiationPhase(this.impl$negotiationValue), state);
    }

    private static long impl$packNegotiationValue(final int phase, final int state) {
        return Integer.toUnsignedLong(phase) << 32 | Integer.toUnsignedLong(state);
    }

    private static int impl$readNegotiationPhase(final long value) {
        return (int) (value >>> 32);
    }

    private static int impl$readNegotiationState(final long value) {
        return (int) value;
    }
}
