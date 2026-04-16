package com.mryu.signin.data;

import net.minecraft.text.Text;

public record SigninResult(
	boolean success,
	Text message,
	PlayerSigninRecord record,
	RewardEntry reward
) {
}
