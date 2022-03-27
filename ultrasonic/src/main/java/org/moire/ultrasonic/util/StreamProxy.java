package org.moire.ultrasonic.util;

import org.moire.ultrasonic.domain.MusicDirectory;
import org.moire.ultrasonic.service.DownloadFile;
import org.moire.ultrasonic.service.Supplier;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.util.StringTokenizer;

import timber.log.Timber;

public class StreamProxy implements Runnable
{
	private Thread thread;
	private boolean isRunning;
	private ServerSocket socket;
	private int port;
	private Supplier<DownloadFile> currentPlaying;

	public StreamProxy(Supplier<DownloadFile> currentPlaying)
	{

		// Create listening socket
		try
		{
			socket = new ServerSocket(0, 0, InetAddress.getByAddress(new byte[]{127, 0, 0, 1}));
			socket.setSoTimeout(5000);
			port = socket.getLocalPort();
			this.currentPlaying = currentPlaying;
		}
		catch (UnknownHostException e)
		{ // impossible
		}
		catch (IOException e)
		{
			Timber.e(e, "IOException initializing server");
		}
	}

	public int getPort()
	{
		return port;
	}

	public void start()
	{
		thread = new Thread(this);
		thread.start();
	}

	public void stop()
	{
		isRunning = false;
		thread.interrupt();
	}

	@Override
	public void run()
	{
		isRunning = true;
		while (isRunning)
		{
			try
			{
				Socket client = socket.accept();
				if (client == null)
				{
					continue;
				}
				Timber.i("Client connected");

				StreamToMediaPlayerTask task = new StreamToMediaPlayerTask(client);
				if (task.processRequest())
				{
					new Thread(task).start();
				}

			}
			catch (SocketTimeoutException e)
			{
				// Do nothing
			}
			catch (IOException e)
			{
				Timber.e(e, "Error connecting to client");
			}
		}
		Timber.i("Proxy interrupted. Shutting down.");
	}

	private class StreamToMediaPlayerTask implements Runnable {
		String localPath;
		Socket client;
		int cbSkip;

		StreamToMediaPlayerTask(Socket client) {
			this.client = client;
		}

		private String readRequest() {
			InputStream is;
			String firstLine;
			try {
				is = client.getInputStream();
				BufferedReader reader = new BufferedReader(new InputStreamReader(is), 8192);
				firstLine = reader.readLine();
			} catch (IOException e) {
				Timber.e(e, "Error parsing request");
				return null;
			}

			if (firstLine == null) {
				Timber.i("Proxy client closed connection without a request.");
				return null;
			}

			StringTokenizer st = new StringTokenizer(firstLine);
			st.nextToken(); // method
			String uri = st.nextToken();
			String realUri = uri.substring(1);
			Timber.i(realUri);

			return realUri;
		}

		boolean processRequest() {
			final String uri = readRequest();
			if (uri == null || uri.isEmpty()) {
				return false;
			}

			// Read HTTP headers
			Timber.i("Processing request: %s", uri);

			try {
				localPath = URLDecoder.decode(uri, Constants.UTF_8);
			} catch (UnsupportedEncodingException e) {
				Timber.e(e, "Unsupported encoding");
				return false;
			}

			Timber.i("Processing request for file %s", localPath);
			if (Storage.INSTANCE.isPathExists(localPath)) return true;

			// Usually the .partial file will be requested here, but sometimes it has already
			// been renamed, so check if it is completed since
			String saveFileName = FileUtil.INSTANCE.getSaveFile(localPath);
			String completeFileName = FileUtil.INSTANCE.getCompleteFile(saveFileName);

			if (Storage.INSTANCE.isPathExists(saveFileName)) {
				localPath = saveFileName;
				return true;
			}

			if (Storage.INSTANCE.isPathExists(completeFileName)) {
				localPath = completeFileName;
				return true;
			}

			Timber.e("File %s does not exist", localPath);
			return false;

		}

		@Override
		public void run()
		{
			Timber.i("Streaming song in background");
			DownloadFile downloadFile = currentPlaying == null? null : currentPlaying.get();
			MusicDirectory.Track song = downloadFile.getTrack();
			long fileSize = downloadFile.getBitRate() * ((song.getDuration() != null) ? song.getDuration() : 0) * 1000 / 8;
			Timber.i("Streaming fileSize: %d", fileSize);

			// Create HTTP header
			String headers = "HTTP/1.0 200 OK\r\n";
			headers += "Content-Type: application/octet-stream\r\n";
			headers += "Connection: close\r\n";
			headers += "\r\n";

			long cbToSend = fileSize - cbSkip;
			OutputStream output = null;
			byte[] buff = new byte[64 * 1024];

			try
			{
				output = new BufferedOutputStream(client.getOutputStream(), 32 * 1024);
				output.write(headers.getBytes());

				if (!downloadFile.isWorkDone())
				{
					// Loop as long as there's stuff to send
					while (isRunning && !client.isClosed())
					{
						// See if there's more to send
						String file = downloadFile.isCompleteFileAvailable() ? downloadFile.getCompleteOrSaveFile() : downloadFile.getPartialFile();
						int cbSentThisBatch = 0;

						AbstractFile storageFile = Storage.INSTANCE.getFromPath(file);
						if (storageFile != null)
						{
							InputStream input = storageFile.getFileInputStream();

							try
							{
								long skip = input.skip(cbSkip);
								int cbToSendThisBatch = input.available();

								while (cbToSendThisBatch > 0)
								{
									int cbToRead = Math.min(cbToSendThisBatch, buff.length);
									int cbRead = input.read(buff, 0, cbToRead);

									if (cbRead == -1)
									{
										break;
									}

									cbToSendThisBatch -= cbRead;
									cbToSend -= cbRead;
									output.write(buff, 0, cbRead);
									output.flush();
									cbSkip += cbRead;
									cbSentThisBatch += cbRead;
								}
							}
							finally
							{
								input.close();
							}

							// Done regardless of whether or not it thinks it is
							if (downloadFile.isWorkDone() && cbSkip >= file.length())
							{
								break;
							}
						}

						// If we did nothing this batch, block for a second
						if (cbSentThisBatch == 0)
						{
							Timber.d("Blocking until more data appears (%d)", cbToSend);
							Util.sleepQuietly(1000L);
						}
					}
				}
				else
				{
					Timber.w("Requesting data for completely downloaded file");
				}
			}
			catch (SocketException socketException)
			{
				Timber.e("SocketException() thrown, proxy client has probably closed. This can exit harmlessly");
			}
			catch (Exception e)
			{
				Timber.e("Exception thrown from streaming task:");
				Timber.e("%s : %s", e.getClass().getName(), e.getLocalizedMessage());
			}

			// Cleanup
			try
			{
				if (output != null)
				{
					output.close();
				}
				client.close();
			}
			catch (IOException e)
			{
				Timber.e("IOException while cleaning up streaming task:");
				Timber.e("%s : %s", e.getClass().getName(), e.getLocalizedMessage());
			}
		}
	}
}