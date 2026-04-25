package com.mryu.signin.client.network;

import com.mryu.signin.data.RewardEntry;
import com.mryu.signin.network.SigninNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.List;

public final class SigninClientActions {
	private SigninClientActions() {
	}

	public static void requestSync() {
		sendEmptyIfAvailable(SigninNetworking.C2S_REQUEST_SYNC);
	}

	public static void signToday() {
		sendEmptyIfAvailable(SigninNetworking.C2S_SIGN);
	}

	public static void makeupLatestMissedDay() {
		sendEmptyIfAvailable(SigninNetworking.C2S_MAKEUP_LATEST_MISSED);
	}

	public static void saveRewards(List<RewardEntry> rewards) {
		if (!ClientPlayNetworking.canSend(SigninNetworking.C2S_SAVE_REWARDS)) {
			return;
		}
		PacketByteBuf buf = PacketByteBufs.create();
		SigninNetworking.writeRewards(buf, rewards);
		ClientPlayNetworking.send(SigninNetworking.C2S_SAVE_REWARDS, buf);
	}

	private static void sendEmptyIfAvailable(Identifier channel) {
		if (!ClientPlayNetworking.canSend(channel)) {
			return;
		}
		ClientPlayNetworking.send(channel, PacketByteBufs.empty());
	}
}
