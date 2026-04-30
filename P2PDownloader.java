import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

public class P2PDownloader {
    static class TorrentInfo {
        int numBlocks;
        int fileSize;
        InetSocketAddress peer1;
        InetSocketAddress peer2;
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
        torrentServer.setSoTimeout(2000); // 2-second timeout

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
        String ip1 = null;
        int port1 = -1;
        String ip2 = null;
        int port2 = -1;
        TorrentInfo info = new TorrentInfo();
        String[] lines = responseStr.split("\n");
        for (String line : responseStr.split("\n")) {
            line = line.trim();
            if (line.equals("400 BAD_FORMAT"))
                System.exit(1);
            else if (line.startsWith("NUM_BLOCKS:"))
                info.numBlocks = Integer.parseInt(line.split(":")[1].trim());
            else if (line.startsWith("FILE_SIZE:"))
                info.fileSize = Integer.parseInt(line.split(":")[1].trim());
            else if (line.startsWith("IP1:"))
                ip1 = line.split(":")[1].trim();
            else if (line.startsWith("PORT1:"))
                port1 = Integer.parseInt(line.split(":")[1].trim());
            else if (line.startsWith("IP2:"))
                ip2 = line.split(":")[1].trim();
            else if (line.startsWith("PORT2:"))
                port2 = Integer.parseInt(line.split(":")[1].trim());
        }

        if(ip1 != null && port1 != -1) info.peer1 = new InetSocketAddress(ip1, port1);
        if(ip2 != null && port2 != -1) info.peer2 = new InetSocketAddress(ip2, port2);

        return info;

    }
    /**
     * Downloads a data block from a peer using TCP.
     *
     * @param peer peer to download file from
     * @param fileName name of file to fetch
     * @param blockNumber block index to fetch
     * @return returns byteLength, or 0 if download failed
     */
    static int downloadDataBlocks(InetSocketAddress peer, String fileName, int blockNumber) throws IOException {
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

        if(lines.length < 3) return 0; // response should have min of 3 lines
        String statusLine = lines[0];
        if(!statusLine.equals("200 OK")) return 0;
        int byteOffset = Integer.parseInt(lines[1].split(":")[1].trim());
        int byteLength = Integer.parseInt(lines[2].split(":")[1].trim());

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

        return totalRead;
    }
    static class PeerDownloadRunnable implements Runnable {
        private final Queue<Integer> blockQueue;
        private final AtomicLong downloadedBytes;
        private final InetSocketAddress peer;
        private final String fileName;

        public PeerDownloadRunnable
                (Queue<Integer> givenBlockQueue, AtomicLong givenDownloadedBytes, InetSocketAddress givenPeer, String givenFileName){

            this.blockQueue = givenBlockQueue;
            this.downloadedBytes = givenDownloadedBytes;
            this.peer = givenPeer;
            this.fileName = givenFileName;

        }
        @Override
        public void run(){
            System.out.println("Thread starting!");
            boolean workToBeDone = true;
            while(workToBeDone){
                Integer block = blockQueue.poll();
                if(block == null){
                    workToBeDone = false;
                    continue;
                }

                try{
                    int attempts = 0;
                    int bytes = 0;
                    while (attempts < 3 && bytes == 0) {
                        bytes = downloadDataBlocks(peer, fileName, block);
                        attempts++;
                    }
                    if (bytes == 0) {
                        blockQueue.add(block);
                    } else {
                        downloadedBytes.addAndGet(bytes);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
    public final static int THREAD_COUNT = 2;
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

        Queue<Integer> blockQueue = new ConcurrentLinkedQueue<>();
        for(int i = 0; i < info.numBlocks; i++){
            blockQueue.add(i);
        }

        AtomicLong downloadedBytes = new AtomicLong(0);

        for(int i = 0; i < THREAD_COUNT; i++){
            TorrentInfo infoForThread = getTorrentMetadata(ip, port, fileName);
            Thread thread1 = new Thread(new PeerDownloadRunnable(blockQueue, downloadedBytes, infoForThread.peer1, fileName));
            Thread thread2 = new Thread(new PeerDownloadRunnable(blockQueue, downloadedBytes, infoForThread.peer2, fileName));
            thread1.start();
            thread2.start();
            // Wait 1 seconds to not overload UDP server
            try {
                Thread.sleep(1000); // 1 second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        System.out.println("Downloading " + fileName + "...");
        while(downloadedBytes.get() < info.fileSize){
            int percent = (int)Math.round((downloadedBytes.get() * 100.0) / info.fileSize);
            System.out.println("Completed " + percent + "% of the download");
            try {
                Thread.sleep(1000); // 1 second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        System.out.println("Download complete");
    }
}
