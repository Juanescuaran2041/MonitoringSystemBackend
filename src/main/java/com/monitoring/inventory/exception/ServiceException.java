package com.monitoring.inventory.exception;

/** Business failure raised by a microservice, carrying the HTTP status the API should answer with. */
public class ServiceException extends RuntimeException {

    private final int status;

    private ServiceException(int status, String message) {
        super(message);
        this.status = status;
    }

    public static ServiceException badRequest(String message) {
        return new ServiceException(400, message);
    }

    public static ServiceException notFound(String message) {
        return new ServiceException(404, message);
    }

    /** The operation was valid but the downstream work failed (e.g. a declined payment). */
    public static ServiceException failed(String message) {
        return new ServiceException(502, message);
    }

    public int getStatus() {
        return status;
    }
}
