package com.mryu.signin.client.state;

import com.mryu.signin.network.PlayerSyncPayload;

public final class ClientSigninState {
	private static PlayerSyncPayload payload;

	private ClientSigninState() {
	}

	public static synchronized void update(PlayerSyncPayload latest) {
		payload = latest;
	}

	public static synchronized PlayerSyncPayload get() {
		return payload;
	}

	public static synchronized void clear() {
		payload = null;
	}
}
