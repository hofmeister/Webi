package com.vonhof.webi.rest;

import com.vonhof.webi.HttpException;
import com.vonhof.webi.WebiContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

/**
 *
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class DefaultExceptionHandler implements ExceptionHandler {
    private static final Logger log = LogManager.getLogger(DefaultExceptionHandler.class);

    @Override
    public Object handle(WebiContext ctxt,Throwable ex) {
        log.fatal("Caught unhandled exception", ex);
        if (ex instanceof HttpException) {
            ErrorMessage out = new ErrorMessage((HttpException)ex);
            ctxt.setStatus(out.getCode() > 0 ? out.getCode() : 500);
            return out;
        }
        try {
            ctxt.sendError(ex);
        } catch (IOException ex1) {
            log.fatal("Failed to send error message", ex1);
        }
        return null;
    }
    
    public class ErrorMessage {
        private final boolean error = true;
        private final String msg;
        private final int code;
        
        public ErrorMessage(HttpException ex) {
            msg = ex.getLocalizedMessage();
            code = ex.getCode();
        }

        public ErrorMessage(String msg, int code) {
            this.msg = msg;
            this.code = code;
        }

        public String getMsg() {
            return msg;
        }

        public int getCode() {
            return code;
        }

        public boolean isError() {
            return error;
        }
        
    }
    
}
