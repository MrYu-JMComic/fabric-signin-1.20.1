package com.mryu.signin.client.input;

import com.mryu.signin.client.gui.SigninScreen;
import com.mryu.signin.client.network.SigninClientActions;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class SigninKeyBindings {
	private static final KeyBinding OPEN_SIGNIN_GUI = KeyBindingHelper.registerKeyBinding(new KeyBinding(
		"key.signin.open_gui",
		InputUtil.Type.KEYSYM,
		GLFW.GLFW_KEY_UNKNOWN,
		"key.categories.signin"
	));

	private SigninKeyBindings() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (OPEN_SIGNIN_GUI.wasPressed()) {
				if (client.player == null || client.world == null) {
					continue;
				}

				if (client.currentScreen instanceof SigninScreen) {
					client.currentScreen.close();
					continue;
				}

				if (client.currentScreen != null) {
					continue;
				}

				client.setScreen(new SigninScreen());
				SigninClientActions.requestSync();
			}
		});
	}
}
