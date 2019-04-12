package org.acestream.sdk.errors;

public class GenericValidationException extends Exception {
    public GenericValidationException() {
    }

    public GenericValidationException(String error) {
        super(error);
    }

    public GenericValidationException(String error, Throwable th) {
        super(error, th);
    }
}
