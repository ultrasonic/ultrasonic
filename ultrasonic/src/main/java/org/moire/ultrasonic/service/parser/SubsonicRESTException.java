package org.moire.ultrasonic.service.parser;

/**
 * @author Sindre Mehus
 * @version $Id$
 */
public class SubsonicRESTException extends Exception
{

	/**
	 *
	 */
	private static final long serialVersionUID = 859440717343258203L;
	private final int code;

	public SubsonicRESTException(int code, String message)
	{
		super(message);
		this.code = code;
	}

	public int getCode()
	{
		return code;
	}
}
