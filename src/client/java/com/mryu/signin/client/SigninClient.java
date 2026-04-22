package com.mryu.signin.client;

import com.mryu.signin.Signin;
import com.mryu.signin.client.gui.SigninScreen;
import com.mryu.signin.client.gui.RewardEditorScreen;
import com.mryu.signin.client.input.SigninKeyBindings;
import com.mryu.signin.client.network.SigninClientActions;
import com.mryu.signin.client.state.ClientSigninState;
import com.mryu.signin.network.SigninNetworking;
import com.mryu.signin.service.SigninNoticeEvent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

public class SigninClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		SigninKeyBindings.register();

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
			ClientSigninState.clear();
			SigninClientActions.requestSync();
		}));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(ClientSigninState::clear));

		ClientPlayNetworking.registerGlobalReceiver(SigninNetworking.S2C_SYNC, (client, handler, buf, responseSender) -> {
			SigninNetworking.ReceivedSyncPacket packet;
			try {
				packet = SigninNetworking.readSyncPacket(buf);
			} catch (RuntimeException exception) {
				Signin.LOGGER.warn("Failed to decode sign-in sync packet on client.", exception);
				return;
			}
			client.execute(() -> {
				ClientSigninState.update(packet.payload());
				playNoticeSound(client, packet.payload().noticeEvent());

				if (packet.openScreen()) {
					client.setScreen(new SigninScreen());
				} else if (client.currentScreen instanceof SigninScreen signinScreen) {
					signinScreen.refreshButtons();
				} else if (client.currentScreen instanceof RewardEditorScreen rewardEditorScreen) {
					rewardEditorScreen.onServerPayloadUpdated();
				}
			});
		});
	}

	private static void playNoticeSound(MinecraftClient client, int noticeEvent) {
		if (client == null || client.getSoundManager() == null) {
			return;
		}
		switch (noticeEvent) {
			case SigninNoticeEvent.SIGN_SUCCESS -> client.getSoundManager().play(
				PositionedSoundInstance.master(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.95F, 1.08F)
			);
			case SigninNoticeEvent.MAKEUP_SUCCESS -> client.getSoundManager().play(
				PositionedSoundInstance.master(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.95F, 1.03F)
			);
			default -> {
			}
		}
	}
}
