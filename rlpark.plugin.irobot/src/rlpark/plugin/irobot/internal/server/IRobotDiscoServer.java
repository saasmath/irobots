package rlpark.plugin.irobot.internal.server;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import rlpark.plugin.irobot.data.IRobotLabels;
import rlpark.plugin.irobot.internal.create.IRobotCreateSerialConnection;
import rlpark.plugin.irobot.internal.descriptors.IRobotSerialDescriptor;
import rlpark.plugin.robot.internal.disco.Server;
import rlpark.plugin.robot.internal.disco.io.DiscoSocket;
import zephyr.plugin.core.api.signals.Listener;

public class IRobotDiscoServer {
  final KeepAliveConnection keepAliveConnection;
  private final IRobotCreateSerialConnection serialConnection;
  private final int port;
  final List<ClientSocket> clients = Collections.synchronizedList(new LinkedList<ClientSocket>());
  private final Listener<IRobotCreateSerialConnection> serialClosedListener = new Listener<IRobotCreateSerialConnection>() {
    @Override
    public void listen(IRobotCreateSerialConnection eventInfo) {
      close();
    }
  };
  private Server server;
  private final Listener<ClientSocket> clientSocketClosedListener = new Listener<ClientSocket>() {
    @Override
    public void listen(ClientSocket clientSocket) {
      clients.remove(clientSocket);
      if (clientSocket.wasSendingAction())
        keepAliveConnection.sendNoClientMessage();
    }
  };

  public IRobotDiscoServer(int port, IRobotCreateSerialConnection serialConnection) {
    this.serialConnection = serialConnection;
    serialConnection.onClosed.connect(serialClosedListener);
    this.port = port;
    keepAliveConnection = new KeepAliveConnection(this);
  }

  public void start() throws IOException {
    server = new Server(port);
    while (server != null && !serialConnection.isClosed()) {
      DiscoSocket discoSocket = server.accept();
      if (discoSocket == null)
        continue;
      ClientSocket clientSocket = new ClientSocket(serialConnection, discoSocket);
      clientSocket.onTerminating.connect(clientSocketClosedListener);
      clients.add(clientSocket);
      new Thread(clientSocket).start();
    }
  }

  public boolean initializeSerialLinkCommunication() {
    for (int trial = 0; trial < 10; trial++) {
      serialConnection.initialize();
      if (!serialConnection.isClosed()) {
        keepAliveConnection.watchdog().start();
        return true;
      }
      try {
        Thread.sleep(4000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    return false;
  }

  public static void runServer(String path, IRobotSerialDescriptor descriptor) {
    boolean result = runServer(IRobotLabels.DiscoDefaultPort, path, descriptor);
    System.exit(result ? 0 : 1);
  }

  public static boolean runServer(int port, String path, IRobotSerialDescriptor descriptor) {
    IRobotCreateSerialConnection serialConnection = new IRobotCreateSerialConnection(path, descriptor);
    IRobotDiscoServer server = new IRobotDiscoServer(port, serialConnection);
    if (server.initializeSerialLinkCommunication())
      try {
        server.start();
        return true;
      } catch (IOException e) {
        e.printStackTrace();
      }
    return false;
  }

  public IRobotCreateSerialConnection connection() {
    return serialConnection;
  }

  public int nbClients() {
    return clients.size();
  }

  public void close() {
    try {
      server.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    server = null;
  }

  public int port() {
    return port;
  }
}
