package com.pingworlds;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Ping Worlds",
	description = "Ping a smart, context-filtered set of worlds and see which are best to log into",
	tags = {"ping", "world", "latency", "login", "hop"}
)
public class PingWorldsPlugin extends Plugin
{
	@Inject
	private PingWorldsConfig config;

	@Override
	protected void startUp() throws Exception
	{
		// M0: the plugin loads but does nothing yet. Real behaviour arrives in later milestones.
		log.debug("Ping Worlds started");
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.debug("Ping Worlds stopped");
	}

	@Provides
	PingWorldsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PingWorldsConfig.class);
	}
}
