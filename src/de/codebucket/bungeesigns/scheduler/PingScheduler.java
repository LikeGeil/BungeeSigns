package de.codebucket.bungeesigns.scheduler;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

import de.codebucket.bungeesigns.BungeeSigns;
import de.codebucket.bungeesigns.event.BungeeSignsPingEvent;
import de.codebucket.bungeesigns.event.ServerChangeStatusEvent;
import de.codebucket.bungeesigns.event.ServerPingResponseEvent;
import de.codebucket.bungeesigns.ping.ServerInfo;
import de.codebucket.bungeesigns.ping.ServerPing;
import de.codebucket.bungeesigns.ping.ServerPing.StatusResponse;

public class PingScheduler implements Runnable, Listener
{
	private final BungeeSigns plugin;
	
	public PingScheduler(BungeeSigns plugin)
	{
		this.plugin = plugin;
		plugin.getServer().getPluginManager().registerEvents(this, plugin);
	}
	
	@Override
	public void run()
	{
		final List<ServerInfo> servers = plugin.getConfigData().getServers();
		BungeeSignsPingEvent event = new BungeeSignsPingEvent(servers);
		Bukkit.getPluginManager().callEvent(event);
		Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this, plugin.getConfigData().getPingInterval() * 20);
	}
	
	@EventHandler
	public void onEvent(BungeeSignsPingEvent e)
	{
		if(!e.isCancelled())
		{
			for(ServerInfo server : e.getServers())
			{
				if(!server.isLocal())
				{
					pingAsync(server);
				}
				else
				{
					final String status = server.getMotd();
					ServerListPingEvent ping = new ServerListPingEvent(new InetSocketAddress(Bukkit.getIp(), Bukkit.getPort()).getAddress(), Bukkit.getMotd(), Bukkit.getOnlinePlayers().length, Bukkit.getMaxPlayers());
					Bukkit.getPluginManager().callEvent(ping);
					server.setProtocol(getBukkitVersion());
					server.setMotd(ping.getMotd());
					server.setPlayerCount(ping.getNumPlayers());
					server.setMaxPlayers(ping.getMaxPlayers());
					server.setPingStart(System.currentTimeMillis());
					server.setPingEnd(System.currentTimeMillis());
			        server.setOnline(true);
			        
			        if(!server.getMotd().equals(status))
					{
						ServerChangeStatusEvent sevent = new ServerChangeStatusEvent(server, server.getMotd());
						BungeeSigns.getInstance().callSyncEvent(sevent);
					}
				}
			}
		}
	}
	
	private void pingAsync(final ServerInfo server)
	{
		final ServerPing ping = server.getPing();
		if(!ping.isFetching())
		{
			Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable()
			{
				@Override
				public void run() 
				{
					long pingStartTime = System.currentTimeMillis();
					ping.setAddress(server.getAddress());
					ping.setTimeout(server.getTimeout());
					ping.setFetching(true);
					
					try 
					{
						final String status = server.getMotd();
						StatusResponse response = ping.fetchData();
						server.setVersion(formatVersion(response.getVersion().getName()));
						server.setProtocol(response.getVersion().getProtocol());
						server.setMotd(response.getDescription());
						server.setPlayerCount(response.getPlayers().getOnline());
						server.setMaxPlayers(response.getPlayers().getMax());
						server.setPingStart(pingStartTime);
						server.setOnline(true);
						
						ServerPingResponseEvent revent = new ServerPingResponseEvent(server, ping, response);
						BungeeSigns.getInstance().callSyncEvent(revent);
						
						if(!server.getMotd().equals(status))
						{
							ServerChangeStatusEvent sevent = new ServerChangeStatusEvent(server, server.getMotd());
							BungeeSigns.getInstance().callSyncEvent(sevent);
						}
					} 
					catch(Exception e)
					{
						server.setOnline(false);
						if(!(e instanceof ConnectException))
						{
							BungeeSigns.getInstance().logConsole(Level.WARNING, "[BungeeSigns] Error fetching data from server " + server.getAddress().toString());
						}
					}
					finally
					{
						ping.setFetching(false);
						server.setPingEnd(System.currentTimeMillis());
					}
				}
			});
		}
	}
	
	private String getBukkitVersion()
	{
		String version = Bukkit.getVersion();
		version = version.replace("(", "");
		version = version.replace(")", "");
		version = version.split(" ")[2];
		return version;
	}
	
	private String formatVersion(String version) 
	{
		char[] numbers = "0123456789".toCharArray();
		for (int i = 0; i < version.length(); i++)
		{
			char c = version.charAt(i);
			for (char ch : numbers) 
			{
				if (ch == c) 
				{
					version = version.substring(i);
					break;
				}
			}
		}

		return version;
	}
}
