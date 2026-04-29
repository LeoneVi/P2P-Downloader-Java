import java.net.*;
import java.io.*;
import java.util.*;
public class P2PDownloader {
    static class TorrentInfo {
        int numBlocks;
        int fileSize;
        String ip1;
        Integer port1;
        String ip2;
        Integer port2;

    }
    static TorrentInfo getTorrentMetadata(String ip, String port, String filename) throws IOException {
        System.out.println("Configuring address...");
        InetAddress address = InetAddress.getByName(ip);
        int portNum = Integer.parseInt(port);
        System.out.println("Remote address is " + address.getHostAddress() + ":" + port);

        System.out.println("Creating socket...");
        DatagramSocket torrentServer = new DatagramSocket();
        torrentServer.setSoTimeout(5000); // 5-second timeout

        // format request
        String request = "GET " + filename + ".torrent\n";
        byte[] requestBytes = request.getBytes();
        System.out.println("Request: [" + request.trim() + "]");

        // Receive data
        byte[] buf = new byte[1024];
        DatagramPacket response = new DatagramPacket(buf, buf.length);
        int attempts = 5;
        boolean received = false;

        for (int i = 0; i < attempts; i++) {
            System.out.println("Attempt " + (i + 1) + "...");
            System.out.println("Requesting " + filename + " from torrent server...");

            // send
            DatagramPacket packet = new DatagramPacket(requestBytes, requestBytes.length, address, portNum);
            torrentServer.send(packet);

            // receive
            try {
                torrentServer.receive(response);
                received = true;
                break; // message received
            } catch (SocketTimeoutException e) {
                System.out.println("No response, retrying...");
            }
        }

        // close socket
        System.out.println("Closing connection to torrent server...");
        torrentServer.close();

        if (!received) {
            System.err.println("recvfrom() failed.");
            System.exit(1);
        }

        String responseStr = new String(buf, 0, response.getLength());
        System.out.println("Received (" + response.getLength() + " bytes):\n" + responseStr);

        // parse response into TorrentInfo
        TorrentInfo info = new TorrentInfo();
        for (String line : responseStr.split("\n")) {
            line = line.trim();
            if (line.startsWith("NUM_BLOCKS:"))
                info.numBlocks = Integer.parseInt(line.split(":")[1].trim());
            else if (line.startsWith("FILE_SIZE:"))
                info.fileSize = Integer.parseInt(line.split(":")[1].trim());
            else if (line.startsWith("IP1:"))
                info.ip1 = line.split(":")[1].trim();
            else if (line.startsWith("PORT1:"))
                info.port1 = Integer.parseInt(line.split(":")[1].trim());
            else if (line.startsWith("IP2:"))
                info.ip2 = line.split(":")[1].trim();
            else if (line.startsWith("PORT2:"))
                info.port2 = Integer.parseInt(line.split(":")[1].trim());
        }
        return info;

    }
    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("usage: P2PDownloader <tracker_ip> <tracker_port> <filename>");
            System.exit(1);
        }
        String ip = args[0];
        String port = args[1];
        String filename = args[2];

        System.out.println("Getting torrent metadata...");

        TorrentInfo info = getTorrentMetadata(ip, port, filename);
    }
}
