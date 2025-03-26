# Overview #

In this assignment, we will build upon the previous client-server approach to download files by incorporating a peer-to-peer approach using both TCP and UDP.

**Goal**: Your goal is to write a network program, referred to as the *client*, that downloads an image file from a swarm of peers that we maintain using a peer-to-peer protocol as specified further below.

The following sections describe the protocol spec, deliverable, submission and auto-grading instructions, and tips and FAQs.

### Peer-to-peer protocol and deliverable ###

In the peer-to-peer approach, the client must first obtain the *torrent metadata* from the torrent server (a.k.a *tracker*) and use this metadata to download data blocks. The torrent metadata contains information about the number and size of blocks constituting the file and peers (`<IP,port>` tuples) from which those blocks may be downloaded.


## Torrent metadata ##

**Torrent metadata request format**: The client must download the torrent metadata for filename by sending a UDP message in the following format:

```
	GET <filename>.torrent\n
```
Thus, to request the torrent metadata for `redsox.jpg`, the client must send a UDP message containing the string `GET redsox.jpg.torrent` (with or without a newline at the end) to the torrent server.

This UDP-based torrent server runs on port 19876. 

**Torrent metadata response format**: The response to the request for torrent metadata is in the following format:

```
	NUM_BLOCKS: 6
	FILE_SIZE: 58241
	IP1: 128.119.245.20
	PORT1: 3456
	IP2: 128.119.245.20
	PORT2: 4321
```

The names of the fields above are self-explanatory. `NUM_BLOCKS` is the number of blocks in the requested file. `FILE_SIZE` is the size of the entire file in bytes. `IP1` and `PORT1` identify the IP address and port number of the first peer, and `IP2` and `PORT2` the second peer. Newline characters are visually implicit in the example above and not shown explicitly.

Each response will contain two randomly chosen valid peer identifiers. You can query the tracker multiple times to get more peer identifiers. However, the tracker is designed to rate-limit the queries, so you may not get responses promptly if you send requests too fast or may not get responses at all as UDP messages can get lost.

## Data blocks ##

Having obtained metadata information using UDP as above, data blocks must be requested using TCP as follows.

**Block request format**: The following request fetches a specific block

```
	GET filename:<block_number>\n
```

where block_number is an integer identifying the block in filename. For example, you may request block 24 in redsox.jpg by sending the string `GET Redsox.jpg:24\n` to any one of the peers received in the torrent metadata above. Note that the servers listed in the client/server option also act as peers and support the above request format to request specific blocks.

Specifying `*’ instead of a block number returns a randomly chosen block

```
	GET filename:*\n
```

**Block response format**: The response to a block request has the following format as that of the whole body. The only difference is that the starting byte offset in the file in general will be non-zero and the size of the block will be much smaller than the size of the file, for example:

```
	200 OK
	BODY_BYTE_OFFSET_IN_FILE: 20000
	BODY_BYTE_LENGTH: 10000

	^#@gdhh#...<bytes of body follow here>
```

All blocks except possibly the last block will be of the same size.

**Testing**: You can test your code by locally running the same server you used in the client-server approach (strongly recommended) or against the server we maintain at `date.cs.umass.edu`. You can also use the same two image files, `test.jpg` and `redsox.jpg`, for testing purposes.

To enable the peer-to-peer mode, you need to set the `-t` command-line option while running the server, so run the server [here](https://bitbucket.org/compnetworks/csp2p/src/master/README.md) as `java -jar bin/PA1.jar -t`.

**Deliverable**: Your client must implement the torrent metadata protocol over a UDP socket to the torrent server's `<IP,port>` specified as a command-line argument, learn `<IP,port>` peer tuples from the torrent server, retrieve all the blocks of `filename` specified as a command-line argument (refer Submission Instructions), store the retrieved body in a local file of the same name in the current directory, and then exit gracefully after closing all sockets.

Speed is of essence in the peer-to-peer approach, so your client must implement a strategy to download the file as fast as possible. The server ports on the peer-to-peer server are intentionally rate-limited to send data slower compared to the client-server server, so you will need to use the peer-to-peer approach to download the file in a reasonable amount of time.

# Submission Instructions #

Same as those for the [client-server approach](https://bitbucket.org/compnetworks/cs-downloader/src/master/README.md#markdown-header-submission-instructions) with one important difference: your client should be named P2PDownloader.ext where `.ext` is the extension as appropriate for your programming language.

Standard autograder disclaimers as before apply.

# Tips, FAQs, etc.#

1. **Early bird gets the worm**: Plan to complete the assignment well before the due date as successful execution of the entire program to download the file may take several minutes in Part B, so debugging and testing may take longer than you might expect, and you are more likely to be able to incorporate help or feedback from us if needed.

2. **Threads are your friends**: As you may have realized, common socket methods are *blocking*, so you are encouraged to use multiple threads to simultaneously download blocks from different peers to improve performance, but be careful in synchronizing concurrent access to shared data structures with threads (which if you have not taken an OS course or do not otherwise have experience using threads may incur a steeper learning curve.)
	* If you are unfamiliar with multithreaded programming, you can alternatively use timeouts on blocking socket calls so that your code is not stuck forever expecting to read something that may take arbitrarily long to arrive if at all. Timeouts will by design be less efficient than using multiple threads.

3. **Document, document, document**: Comment your code as much as possible keeping in mind that documentation is as much for your own benefit as for the benefit of others who read your code, especially if we happen to need to manually grade your submission.

4. **Anything that can go wrong will**: Your connections will get closed if you send bad commands, and at random times, your connection may also get closed for no good reason, just like in real-world swarms, as the server periodically randomizes the peer-to-peer ports, so your code should be resilient to unexpected failures.

5. **Find good friends and keep 'em**: The total number of open connections your client can use (across all peer ports) will be limited to a small number based on your IP address, so be careful about remembering to close idle connections because if you try to open more connections than the limit, they will get immediately closed, and furthermore, the system will automatically close connections that have been idle for over a minute, so forgetting to close connections in the long past doesn't haunt you.

6. The peer-to-peer server has been intentionally rate-limited to slow down transfer rates compared to the client-server server so that your peer-to-peer client can leverage other peers to speed it up; in particular, your client-server client using the default 18765 port will be much slower now.

7. Reminders: 
	1. Reminder: TCP != UDP. All data transfer uses TCP, but the torrent metadata tracker uses UDP, so you can not make a TCP connection to or telnet to a UDP server, however you can use `UDPTelnet.java` from the provided “hello world” examples or a command-line tool like `nc` to manually (non-programmatically) “speak” the torrent server protocol on a console just for checking that it works as described.
	
	2. Reminder: You are strongly encouraged to explore and use any convenient methods supported by the Java/Python/your-favorite-language's socket API, but make sure to remember tip #3 from the client-server assignment.
	
	3. Reminder: Blocks are numbered starting from 0, and block offsets (the position of the first byte of the block in the file) start from 0.
	
	4. Reminder: The UDP torrent metadata server is rate-limited, so not every request may receive a response (which goes without saying with UDP in general anyway).
	
	5. Reminder: The data block peer servers are rate-limited, so data may trickle in at its own sweet pace (which goes without saying for a TCP bytestream in general anyway). 
	
8. You have another utility command called `GETHDR` available to just get the header, which sends only the header, not the body, and has been provided to quickly check that the server ports are up and running as expected. (Your client does not need to use this command.)

More tips or FAQs to be added here based on class questions.



