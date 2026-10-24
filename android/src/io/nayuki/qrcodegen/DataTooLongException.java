package io.nayuki.qrcodegen;

/** Thrown when a payload exceeds the capacity of the selected QR code. */
public final class DataTooLongException extends IllegalArgumentException {
    private static final long serialVersionUID=1L;
    public DataTooLongException(String message){super(message);}
}
