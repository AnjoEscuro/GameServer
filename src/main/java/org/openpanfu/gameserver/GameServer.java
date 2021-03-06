/**
 * This file is part of openPanfu, a project that imitates the Flex remoting
 * and gameservers of Panfu.
 *
 * @category GameServer
 * @package org.openpanfu.gameserver
 * @author Altro50 <altro50@msn.com>
 */

package org.openpanfu.gameserver;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.openpanfu.gameserver.commands.Commands;
import org.openpanfu.gameserver.commands.Help;
import org.openpanfu.gameserver.database.Database;
import org.openpanfu.gameserver.games.multiplayer.FourBoom;
import org.openpanfu.gameserver.games.multiplayer.RockPaperScissors;
import org.openpanfu.gameserver.handler.Handler;
import org.openpanfu.gameserver.plugin.PluginLoader;
import org.openpanfu.gameserver.plugin.PluginManager;
import org.openpanfu.gameserver.sessions.SessionManager;
import org.openpanfu.gameserver.util.Key;
import org.openpanfu.gameserver.util.Logger;

import java.io.FileInputStream;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;

public class GameServer {
    private static List<GameServer> gameServers = new ArrayList<GameServer>();
    private static Properties properties = new Properties();

    private int id;
    private String name;
    private int port;
    private boolean chatEnabled = false;
    private SessionManager sessionManager;
    private String secretKey = "";
    private Channel channel;

    // Multiplayer games

    private FourBoom fourBoom;
    private RockPaperScissors rockPaperScissors;

    public GameServer(int id, String name, int port) {
        this.id = id;
        this.name = name;
        this.port = port;
        this.sessionManager = new SessionManager();
        this.chatEnabled = (Integer.valueOf(GameServer.getProperties().getProperty("chat.forcesafechat")) == 0);
        this.fourBoom = new FourBoom();
        this.rockPaperScissors = new RockPaperScissors();
        Logger.info("["+this.id+"] Starting gameserver on port: " + port);

        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class);
            b.childHandler(new GameServerInitializer(this));
            this.channel = b.bind(this.port).sync().channel();
        } catch (Exception e) {
            Logger.error("Failed to create channel! (Make sure the port isn't already in use!)");
            e.printStackTrace();
        }
    }

    public int getId()
    {
        return this.id;
    }

    public int getPort()
    {
        return this.port;
    }

    public String getName()
    {
        return this.name;
    }

    public static void main(String[] arguments) throws SQLException, InterruptedException
    {
        try {
            properties.load(new FileInputStream("GameServer.properties"));
        } catch (Exception e) {
            Logger.error("There was an issue loading the properties file.");
            e.printStackTrace();
            return;
        }
        Handler.initialize();
        if(Database.connect()) {
            Connection database = Database.getConnection();
            Statement st = database.createStatement();
            String query = ("SELECT * FROM gameservers ORDER BY id;");
            ResultSet resultSet = st.executeQuery(query);
            while(resultSet.next()) {
                int id = resultSet.getInt("id");
                String name = resultSet.getString("name");
                int port = resultSet.getInt("port");
                GameServer gameServer = new GameServer(id, name, port);
                gameServers.add(gameServer);
                try {
                    Key keygen = new Key(64, ThreadLocalRandom.current());
                    gameServer.setKey(keygen.nextString());
                    PreparedStatement preparedStatement = database.prepareStatement("UPDATE gameservers SET secret_key = ? where id = ?");
                    preparedStatement.setString(1, gameServer.getKey());
                    preparedStatement.setInt(2, gameServer.getId());
                    preparedStatement.executeUpdate();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            database.close();
            for(GameServer gs : gameServers) {
                gs.updateUserCount();
                Logger.info("Gameserver " + gs.name + " is ready for connections.");
            }
        } else {
            Logger.error("HALT! The database connection could not be initialized!");
        }
        Commands.registerCommand("help", new Help());
        PluginManager.loadPlugins("plugins");
    }

    public static Properties getProperties()
    {
        return properties;
    }

    public SessionManager getSessionManager()
    {
        return sessionManager;
    }

    public void updateUserCount()
    {
        try {
            Connection database = Database.getConnection();
            PreparedStatement preparedStatement = database.prepareStatement("UPDATE gameservers SET player_count = ? where id = ?");
            preparedStatement.setInt(1, this.sessionManager.getUserCount());
            preparedStatement.setInt(2, this.id);
            preparedStatement.executeUpdate();
            database.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static User getUserById(int userId)
    {
        for(GameServer gs : gameServers) {
            User u = gs.getSessionManager().getUserById(userId);
            if(u != null) {
                return u;
            }
        }
        return null;
    }

    public boolean isChatEnabled() {
        return chatEnabled;
    }

    public FourBoom getFourBoom() {
        return fourBoom;
    }

    public RockPaperScissors getRockPaperScissors() {
        return rockPaperScissors;
    }

    public String getKey() {
        return secretKey;
    }

    public void setKey(String secretKey) {
        this.secretKey = secretKey;
    }
}
