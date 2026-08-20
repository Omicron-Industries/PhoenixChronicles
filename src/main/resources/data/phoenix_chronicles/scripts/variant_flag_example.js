// Example KubeJS script demonstrating a conditional flag for quest variants.

// This event runs once when the server starts.
StartupEvents.postInit(event => {
    // Register a flag that is true only when the player is holding a Nether Star.
    // This is a no-argument function. We get the player from KJS's global Client object.
    PhoenixQuestFlags.registerCondition('holding_nether_star', () => {
        const player = Client.player;
        // If there's no player (e.g., context is the server starting up), it can't be true.
        if (!player) {
            return false;
        }
        // Check the player's main-hand item.
        return player.mainHandItem.id === 'minecraft:nether_star';
    });
});
