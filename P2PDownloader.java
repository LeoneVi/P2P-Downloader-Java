import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class P2PDownloader {
    static class TorrentInfo {
        int numBlocks;
        int fileSize;
        String ip1;
        Integer port1;
        String ip2;
        Integer port2;

    }
    /**
     * Establishes a UDP connection to download torrent information
     *
     * @param ip peer IP address
     * @param port peer port number
     * @param fileName name of file to fetch
     * @return TorrentInfo, an object that stores all fetched torrent information
     */
    static TorrentInfo getTorrentMetadata(String ip, String port, String fileName) throws IOException {
        System.out.println("Configuring address...");
        InetAddress address = InetAddress.getByName(ip);
        int portNum = Integer.parseInt(port);
        System.out.println("Remote address is " + address.getHostAddress() + ":" + port);

        System.out.println("Creating socket...");
        DatagramSocket torrentServer = new DatagramSocket();
        torrentServer.setSoTimeout(5000); // 5-second timeout

        // format request
        String request = "GET " + fileName + ".torrent\n";
        byte[] requestBytes = request.getBytes();
        System.out.println("Request: [" + request.trim() + "]");

        // Receive data
        byte[] buf = new byte[1024];
        DatagramPacket response = new DatagramPacket(buf, buf.length);
        int attempts = 5;
        boolean received = false;

        for (int i = 0; i < attempts; i++) {
            System.out.println("Attempt " + (i + 1) + "...");
            System.out.println("Requesting " + fileName + " from torrent server...");

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
            if (line.equals("400 BAD_FORMAT"))
                System.exit(1);
            else if (line.startsWith("NUM_BLOCKS:"))
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
    /**
     * Downloads a data block from a peer using TCP.
     *
     * @param peer peer to download file from
     * @param fileName name of file to fetch
     * @param blockNumber block index to fetch
     * @return true if the block was successfully downloaded, false otherwise
     */
    static boolean downloadDataBlocks(InetSocketAddress peer, String fileName, int blockNumber) throws IOException {
        Socket clientSocket = new Socket();
        clientSocket.connect(peer);
        InputStream inFromServer = clientSocket.getInputStream();
        OutputStream outToServer = clientSocket.getOutputStream();

        // Make GET request
        String req = "GET " + fileName + ":" + blockNumber + "\n";
        outToServer.write(req.getBytes());

        // Read response
        ByteArrayOutputStream headerBuffer = new ByteArrayOutputStream();
        int b;
        boolean prevWasNewline = false;
        while ((b = inFromServer.read()) != -1) {
            headerBuffer.write(b);
            if (prevWasNewline && b == '\n') {
                break;
            }
            prevWasNewline = (b == '\n');
        }

        String header = headerBuffer.toString();
        String[] lines = header.split("\n");

        String statusLine = lines[0];
        int byteOffset = Integer.parseInt(lines[1].split(":")[1].trim());
        int byteLength = Integer.parseInt(lines[2].split(":")[1].trim());
        if(!statusLine.equals("200 OK")) return false;

        byte[] fileData = new byte[byteLength];
        int totalRead = 0;
        while (totalRead < byteLength) {
            int bytesRead = inFromServer.read(fileData, totalRead, byteLength - totalRead);
            if (bytesRead == -1) break;
            totalRead += bytesRead;
        }
        RandomAccessFile file = new RandomAccessFile(fileName, "rw");
        file.seek(byteOffset);
        file.write(fileData);
        file.close();

        outToServer.close();
        inFromServer.close();
        clientSocket.close();

        return true;
    }
    static final String USAGE = "usage: P2PDownloader <tracker_ip> <tracker_port> <filename>";
    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println(USAGE);
            System.exit(1);
        }
        String fileName = args[0];
        String ip = args[1];
        String port = args[2];

        System.out.println("Getting torrent metadata...");

        TorrentInfo info = getTorrentMetadata(ip, port, fileName);
        InetSocketAddress peer1 = new InetSocketAddress(info.ip1, info.port1);
        InetSocketAddress peer2 = new InetSocketAddress(info.ip2, info.port2);

        Queue<Integer> blockQueue = new ConcurrentLinkedQueue<>();
        for(int i = 0; i < info.numBlocks; i++){
            blockQueue.add(i);
        }

        while(!blockQueue.isEmpty()){
            if(downloadDataBlocks(peer1, fileName, blockQueue.element())){
                System.out.println("Downloaded data from peer 1");
                blockQueue.remove();
            }
            if(!blockQueue.isEmpty() && downloadDataBlocks(peer2, fileName, blockQueue.element())){
                System.out.println("Downloaded data from peer 2");
                blockQueue.remove();
            }
        }
    }
}
