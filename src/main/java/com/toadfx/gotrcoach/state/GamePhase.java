package com.toadfx.gotrcoach.state;

public enum GamePhase
{
	/** Not in the minigame. */
	IDLE,
	/** Inside the temple between rounds. */
	WAITING,
	/** The rift is open and the altars are not yet active. */
	MINING,
	/** Altars are open. */
	CRAFTING,
	/** The guardian is charged or dead; the rifts stay open about 30 seconds. */
	CLOSING,
	/** The round has finished. */
	ENDED
}
