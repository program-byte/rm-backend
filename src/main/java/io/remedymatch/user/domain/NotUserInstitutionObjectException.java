package io.remedymatch.user.domain;

/**
 * Der angefragte Objekt gehoert nicht der Institution des angemeldetes Users
 */
public class NotUserInstitutionObjectException extends Exception {
	private static final long serialVersionUID = 8611906627464247576L;
	
	public NotUserInstitutionObjectException(String message) {
		super(message);
	}
}
