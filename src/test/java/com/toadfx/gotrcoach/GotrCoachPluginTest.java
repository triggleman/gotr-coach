package com.toadfx.gotrcoach;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Launches RuneLite with GOTR Coach loaded. Run this from IntelliJ, or use the Gradle "run" task. */
public class GotrCoachPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(GotrCoachPlugin.class);
		RuneLite.main(args);
	}
}
