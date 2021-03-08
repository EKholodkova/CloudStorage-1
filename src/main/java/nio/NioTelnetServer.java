package nio;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class NioTelnetServer {
	private final ByteBuffer buffer = ByteBuffer.allocate(512);

	public static final String LS_COMMAND = "\tls          view all files from current directory\n";
	public static final String MKDIR_COMMAND = "\tmkdir       create directory\n";
	public static final String TOUCH_COMMAND = "\ttouch       create file\n";
	public static final String CD_COMMAND = "\tcd          move through the folder tree\n";
	public static final String RM_COMMAND = "\trm          remove object\n";
	public static final String COPY_COMMAND = "\tcopy        copy file\n";
	public static final String CAT_COMMAND = "\tcat         show file content\n";

	private Path currentDir = Path.of("server");

	public NioTelnetServer() throws IOException {
		ServerSocketChannel server = ServerSocketChannel.open(); // открыли
		server.bind(new InetSocketAddress(1234));
		server.configureBlocking(false); // ВАЖНО
		Selector selector = Selector.open();
		server.register(selector, SelectionKey.OP_ACCEPT);
		System.out.println("Server started");
		while (server.isOpen()) {
			selector.select();
			var selectionKeys = selector.selectedKeys();
			var iterator = selectionKeys.iterator();
			while (iterator.hasNext()) {
				var key = iterator.next();
				if (key.isAcceptable()) {
					handleAccept(key, selector);
				} else if (key.isReadable()) {
					handleRead(key, selector);
				}
				iterator.remove();
			}
		}
	}

	private void handleRead(SelectionKey key, Selector selector) throws IOException {
		SocketChannel channel = (SocketChannel) key.channel();
		int readBytes = channel.read(buffer);
		if (readBytes < 0) {
			channel.close();
			return;
		} else if (readBytes == 0) {
			return;
		}

		buffer.flip();
		StringBuilder sb = new StringBuilder();
		while (buffer.hasRemaining()) {
			sb.append((char) buffer.get());
		}
		buffer.clear();

		// TODO: 05.03.2021
		// touch (имя файла) - создание файла
		// mkdir (имя директории) - создание директории
		// cd (path) - перемещение по дереву папок
		// rm (имя файла или папки) - удаление объекта
		// copy (src, target) - копирование файла
		// cat (имя файла) - вывод в консоль содержимого

		if (key.isValid()) {
			String command = sb.toString()
					.replace("\n", "")
					.replace("\r", "");
			if ("--help".equals(command)) {
				sendMessage(LS_COMMAND, selector);
				sendMessage(MKDIR_COMMAND, selector);
				sendMessage(TOUCH_COMMAND, selector);
				sendMessage(CD_COMMAND, selector);
				sendMessage(RM_COMMAND, selector);
				sendMessage(COPY_COMMAND, selector);
				sendMessage(CAT_COMMAND, selector);
			} else if ("ls".equals(command)) {
				sendMessage(getFilesList().concat("\n"), selector);
			} else if (command.contains("touch")){
				String fileName = getArgFromCommand(command, "touch");
				sendMessage(createFile(fileName).concat("\n"), selector);
			} else if (command.contains("mkdir")) {
				String dir = getArgFromCommand(command, "mkdir");
				sendMessage(createDir(dir).concat("\n"), selector);
			} else if (command.contains("cd")) {
				String path = getArgFromCommand(command, "cd");
				sendMessage(changeDir(path).concat("\n"), selector);
			} else if (command.contains("rm")) {
				String obj = getArgFromCommand(command, "rm");
				sendMessage(removeObj(obj).concat("\n"), selector);
			} else if (command.contains("copy")) {
				List<String> args = getTwoArgsFromCommand(command);
				String src = args.get(0);
				String target = args.get(1);
				sendMessage(copyFile(src, target).concat("\n"), selector);
			} else if (command.contains("cat")) {
				String fileName = getArgFromCommand(command, "cat");
				sendMessage(showFileContent(fileName).concat("\n"), selector);
			} else if ("exit".equals(command)) {
				System.out.println("Client logged out. IP: " + channel.getRemoteAddress());
				channel.close();
				return;
			}
		}
		sendName(channel);
	}

	private void sendName(SocketChannel channel) throws IOException {
		channel.write(
				ByteBuffer.wrap(channel
						.getRemoteAddress().toString()
						.concat(">: ")
						.getBytes(StandardCharsets.UTF_8)
				)
		);
	}



	private String createDir(String dir) throws IOException {
		Path path = Path.of(currentDir.toString(), dir);
		if(!Files.exists(path)) {
			Path newDir = Files.createDirectory(path);
			return dir + " was created successfully";
		}
		return dir + " already exists";
	}

	private String createFile(String fileName) throws IOException {
		Path path = Path.of(currentDir.toString(), fileName);

		if(!Files.exists(path)) {
			Files.createFile(path);
			return fileName + " was created successfully";
		}
		return fileName + " already exists";
	}

	private String changeDir(String path) {
		if(path.equals("..")) {
			if(currentDir.getNameCount() > 1) {
				currentDir = currentDir.subpath(0, currentDir.getNameCount() -1);
			} else {
				return "Current directory is root  - " + currentDir.toString();
			}
		} else {
			Path requestedPath = Path.of(currentDir + File.separator + path);
			if(Files.exists(requestedPath)) {
				currentDir = Paths.get(currentDir + File.separator + path);
			} else {
				return "There is no such directory";
			}
		}
		return "Dir was changed to " + currentDir.toString();
	}

	private String removeObj(String objName) throws IOException {
		Path path = Path.of( currentDir.toString() + File.separator + objName);
		if(Files.exists(path)) {
			Files.delete(path);
		} else {
			return "There is no such file or directory";
		}
		return "File was deleted";
	}

	private String copyFile(String src, String target) throws IOException {
		Path sourcePath = Path.of(currentDir.toString() + File.separator + src);
		if(!Files.exists(sourcePath)) {
			return "Source path does not exist";
		}
		Path targetPath = Path.of(currentDir.toString() + File.separator + target);
		if(!Files.exists(targetPath)) {
			return "Destination path does not exist";
		}
		Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
		return "Copy was created";
	}

	private String showFileContent(String fileName) throws IOException {
		Path path = Path.of(currentDir + File.separator + fileName);
		if(!Files.exists(path)) {
			return "File does not exist";
		}
		StringBuilder sb = new StringBuilder();
		byte[] bytes = Files.readAllBytes(path);
		for (byte b : bytes) {
			sb.append((char) b);
		}
		return sb.toString();
	}

	private String getFilesList() {
		return String.join("\t", new File(currentDir.toString()).list());
	}

	private String getArgFromCommand(String inputStr, String command) {
		String arg = inputStr.substring(command.length());
		arg = arg.trim();
		return arg;
	}

	private List<String> getTwoArgsFromCommand(String inputStr) {
		String[] lines = inputStr.split(" ");
		List<String> args = new ArrayList<>();
		String src = null;
		String target = null;
		int i = 1;
		for (; i < lines.length; i++) {
			if(!lines[i].isEmpty()) {
				src = lines[i];
				args.add(src);
				i++;
				break;
			}
		}
		for(; i < lines.length; i++) {
			if(!lines[i].isEmpty()) {
				target = lines[i];
				args.add(target);
				break;
			}
		}
		return args;
	}

	private void sendMessage(String message, Selector selector) throws IOException {
		for (SelectionKey key : selector.keys()) {
			if (key.isValid() && key.channel() instanceof SocketChannel) {
				((SocketChannel) key.channel())
						.write(ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8)));
			}
		}
	}

	private void handleAccept(SelectionKey key, Selector selector) throws IOException {
		SocketChannel channel = ((ServerSocketChannel) key.channel()).accept();
		channel.configureBlocking(false);
		System.out.println("Client accepted. IP: " + channel.getRemoteAddress());
		channel.register(selector, SelectionKey.OP_READ, "some attach");
		channel.write(ByteBuffer.wrap("Hello user!\n".getBytes(StandardCharsets.UTF_8)));
		channel.write(ByteBuffer.wrap("Enter --help for support info\n".getBytes(StandardCharsets.UTF_8)));
		sendName(channel);
	}

	public static void main(String[] args) throws IOException {
		new NioTelnetServer();
	}
}
