/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.moire.ultrasonic.service.ssl;

import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.scheme.HostNameResolver;
import org.apache.http.conn.scheme.LayeredSocketFactory;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Layered socket factory for TLS/SSL connections.
 * <p>
 * SSLSocketFactory can be used to validate the identity of the HTTPS server against a list of
 * trusted certificates and to authenticate to the HTTPS server using a private key.
 * <p>
 * SSLSocketFactory will enable server authentication when supplied with
 * a {@link KeyStore trust-store} file containing one or several trusted certificates. The client
 * secure socket will reject the connection during the SSL session handshake if the target HTTPS
 * server attempts to authenticate itself with a non-trusted certificate.
 * <p>
 * Use JDK keytool utility to import a trusted certificate and generate a trust-store file:
 * <pre>
 *     keytool -import -alias "my server cert" -file server.crt -keystore my.truststore
 *    </pre>
 * <p>
 * In special cases the standard trust verification process can be bypassed by using a custom
 * {@link TrustStrategy}. This interface is primarily intended for allowing self-signed
 * certificates to be accepted as trusted without having to add them to the trust-store file.
 * <p>
 * The following parameters can be used to customize the behavior of this
 * class:
 * <ul>
 * <li>{@link org.apache.http.params.CoreConnectionPNames#CONNECTION_TIMEOUT}</li>
 * <li>{@link org.apache.http.params.CoreConnectionPNames#SO_TIMEOUT}</li>
 * </ul>
 * <p>
 * SSLSocketFactory will enable client authentication when supplied with
 * a {@link KeyStore key-store} file containing a private key/public certificate
 * pair. The client secure socket will use the private key to authenticate
 * itself to the target HTTPS server during the SSL session handshake if
 * requested to do so by the server.
 * The target HTTPS server will in its turn verify the certificate presented
 * by the client in order to establish client's authenticity
 * <p>
 * Use the following sequence of actions to generate a key-store file
 * </p>
 * <ul>
 * <li>
 * <p>
 * Use JDK keytool utility to generate a new key
 * <pre>keytool -genkey -v -alias "my client key" -validity 365 -keystore my.keystore</pre>
 * For simplicity use the same password for the key as that of the key-store
 * </p>
 * </li>
 * <li>
 * <p>
 * Issue a certificate signing request (CSR)
 * <pre>keytool -certreq -alias "my client key" -file mycertreq.csr -keystore my.keystore</pre>
 * </p>
 * </li>
 * <li>
 * <p>
 * Send the certificate request to the trusted Certificate Authority for signature.
 * One may choose to act as her own CA and sign the certificate request using a PKI
 * tool, such as OpenSSL.
 * </p>
 * </li>
 * <li>
 * <p>
 * Import the trusted CA root certificate
 * <pre>keytool -import -alias "my trusted ca" -file caroot.crt -keystore my.keystore</pre>
 * </p>
 * </li>
 * <li>
 * <p>
 * Import the PKCS#7 file containg the complete certificate chain
 * <pre>keytool -import -alias "my client key" -file mycert.p7 -keystore my.keystore</pre>
 * </p>
 * </li>
 * <li>
 * <p>
 * Verify the content the resultant keystore file
 * <pre>keytool -list -v -keystore my.keystore</pre>
 * </p>
 * </li>
 * </ul>
 *
 * @since 4.0
 */
public class SSLSocketFactory implements LayeredSocketFactory
{

	public static final String TLS = "TLS";
	public static final X509HostnameVerifier ALLOW_ALL_HOSTNAME_VERIFIER = new AllowAllHostnameVerifier();

	/**
	 * The default factory using the default JVM settings for secure connections.
	 */
	private final javax.net.ssl.SSLSocketFactory socketFactory;
	private final HostNameResolver nameResolver;
	private volatile X509HostnameVerifier hostnameVerifier;

	private static SSLContext createSSLContext(String algorithm, final KeyStore keystore, final String keyStorePassword, final SecureRandom random, final TrustStrategy trustStrategy) throws NoSuchAlgorithmException, KeyStoreException, UnrecoverableKeyException, KeyManagementException
	{
		if (algorithm == null)
		{
			algorithm = TLS;
		}

		KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		keyManagerFactory.init(keystore, keyStorePassword != null ? keyStorePassword.toCharArray() : null);
		KeyManager[] keyManagers = keyManagerFactory.getKeyManagers();
		TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		trustManagerFactory.init(keystore);

		TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();

		if (trustManagers != null && trustStrategy != null)
		{
			for (int i = 0; i < trustManagers.length; i++)
			{
				TrustManager tm = trustManagers[i];

				if (tm instanceof X509TrustManager)
				{
					trustManagers[i] = new TrustManagerDecorator((X509TrustManager) tm, trustStrategy);
				}
			}
		}

		SSLContext sslcontext = SSLContext.getInstance(algorithm);
		sslcontext.init(keyManagers, trustManagers, random);

		return sslcontext;
	}

	/**
	 * @since 4.1
	 */
	public SSLSocketFactory(String algorithm, final KeyStore keystore, final String keyStorePassword, final SecureRandom random, final TrustStrategy trustStrategy, final X509HostnameVerifier hostnameVerifier) throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException
	{
		this(createSSLContext(algorithm, keystore, keyStorePassword, random, trustStrategy), hostnameVerifier);
	}

	/**
	 * @since 4.1
	 */
	public SSLSocketFactory(final TrustStrategy trustStrategy, final X509HostnameVerifier hostnameVerifier) throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException
	{
		this(TLS, null, null, null, trustStrategy, hostnameVerifier);
	}

	/**
	 * @since 4.1
	 */
	public SSLSocketFactory(final SSLContext sslContext, final X509HostnameVerifier hostnameVerifier)
	{
		super();
		this.socketFactory = sslContext.getSocketFactory();
		this.hostnameVerifier = hostnameVerifier;
		this.nameResolver = null;
	}

	@SuppressWarnings("cast")
	public Socket createSocket() throws IOException
	{
		// the cast makes sure that the factory is working as expected
		return this.socketFactory.createSocket();
	}

	/**
	 * @since 4.1
	 */
	public Socket connectSocket(final Socket sock, final InetSocketAddress remoteAddress, final InetSocketAddress localAddress, final HttpParams params) throws IOException
	{
		if (remoteAddress == null)
		{
			throw new IllegalArgumentException("Remote address may not be null");
		}

		if (params == null)
		{
			throw new IllegalArgumentException("HTTP parameters may not be null");
		}

		SSLSocket sslSocket = (SSLSocket) (sock != null ? sock : createSocket());

		if (localAddress != null)
		{
			//            sslSocket.setReuseAddress(HttpConnectionParams.getSoReuseaddr(params));
			sslSocket.bind(localAddress);
		}

		int connTimeout = HttpConnectionParams.getConnectionTimeout(params);
		int soTimeout = HttpConnectionParams.getSoTimeout(params);

		try
		{
			sslSocket.connect(remoteAddress, connTimeout);
		}
		catch (SocketTimeoutException ex)
		{
			throw new ConnectTimeoutException(String.format("Connect to %s/%s timed out", remoteAddress.getHostName(), remoteAddress.getAddress()));
		}

		sslSocket.setSoTimeout(soTimeout);

		if (this.hostnameVerifier != null)
		{
			try
			{
				this.hostnameVerifier.verify(remoteAddress.getHostName(), sslSocket);
				// verifyHostName() didn't blowup - good!
			}
			catch (IOException iox)
			{
				// close the socket before re-throwing the exception
				try
				{
					sslSocket.close();
				}
				catch (Exception x)
				{ /*ignore*/ }
				throw iox;
			}
		}

		return sslSocket;
	}


	/**
	 * Checks whether a socket connection is secure.
	 * This factory creates TLS/SSL socket connections
	 * which, by default, are considered secure.
	 * <br/>
	 * Derived classes may override this method to perform
	 * runtime checks, for example based on the cypher suite.
	 *
	 * @param sock the connected socket
	 * @return <code>true</code>
	 * @throws IllegalArgumentException if the argument is invalid
	 */
	public boolean isSecure(final Socket sock) throws IllegalArgumentException
	{
		if (sock == null)
		{
			throw new IllegalArgumentException("Socket may not be null");
		}

		// This instanceof check is in line with createSocket() above.
		if (!(sock instanceof SSLSocket))
		{
			throw new IllegalArgumentException("Socket not created by this factory");
		}

		// This check is performed last since it calls the argument object.
		if (sock.isClosed())
		{
			throw new IllegalArgumentException("Socket is closed");
		}

		return true;
	}

	/**
	 * @since 4.1
	 */
	public Socket createLayeredSocket(final Socket socket, final String host, final int port, final boolean autoClose) throws IOException
	{
		SSLSocket sslSocket = (SSLSocket) this.socketFactory.createSocket(socket, host, port, autoClose);

		if (this.hostnameVerifier != null)
		{
			this.hostnameVerifier.verify(host, sslSocket);
		}

		// verifyHostName() didn't blowup - good!
		return sslSocket;
	}

	/**
	 * @deprecated Use {@link #connectSocket(Socket, InetSocketAddress, InetSocketAddress, HttpParams)}
	 */
	@Deprecated
	public Socket connectSocket(final Socket socket, final String host, int port, final InetAddress localAddress, int localPort, final HttpParams params) throws IOException
	{
		InetSocketAddress local = null;

		if (localAddress != null || localPort > 0)
		{
			// we need to bind explicitly
			if (localPort < 0)
			{
				localPort = 0; // indicates "any"
			}

			local = new InetSocketAddress(localAddress, localPort);
		}

		InetAddress remoteAddress;

		if (this.nameResolver != null)
		{
			remoteAddress = this.nameResolver.resolve(host);
		}
		else
		{
			remoteAddress = InetAddress.getByName(host);
		}

		InetSocketAddress remote = new InetSocketAddress(remoteAddress, port);

		return connectSocket(socket, remote, local, params);
	}

	/**
	 * @deprecated Use {@link #createLayeredSocket(Socket, String, int, boolean)}
	 */
	@Deprecated
	public Socket createSocket(final Socket socket, final String host, int port, boolean autoClose) throws IOException
	{
		return createLayeredSocket(socket, host, port, autoClose);
	}
}