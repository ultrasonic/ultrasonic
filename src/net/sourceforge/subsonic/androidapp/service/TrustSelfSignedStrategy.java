package net.sourceforge.subsonic.androidapp.service;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import net.sourceforge.subsonic.androidapp.service.ssl.TrustStrategy;

public class TrustSelfSignedStrategy implements TrustStrategy {

	@Override
	public boolean isTrusted(X509Certificate[] chain, String authType)
			throws CertificateException {
		// TODO Auto-generated method stub
		return false;
	}

}
